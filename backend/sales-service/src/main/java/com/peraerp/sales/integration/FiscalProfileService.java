package com.peraerp.sales.integration;

import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Configuración de factura electrónica de cada empresa en su propio {@code factura_e.ini}, como en
 * DimproCristalWin: cada cliente tiene su fichero, con su certificado o sus claves, y nadie depende del
 * certificado de otro.
 *
 * <p>El fichero vive en {@code <pera.fiscal.config-dir>/<NIF del emisor>/factura_e.ini}. Nunca se sube a
 * Git (ver {@code .gitignore}); en el repositorio solo está la plantilla
 * {@code config/empresas/EJEMPLO/factura_e.ini.example}.</p>
 *
 * <p>Si una empresa no tiene fichero, todo sigue como antes: el certificado se carga cifrado desde
 * Conexiones. Si lo tiene, manda el fichero, pero lo guardado en Conexiones no se borra ni se cambia.</p>
 */
@Service
public class FiscalProfileService {
    private static final Logger log = LoggerFactory.getLogger(FiscalProfileService.class);
    static final String FILE_NAME = "factura_e.ini";
    /** Marca de {@code fiscal_connections.secret_cipher} en la fila que se crea para una empresa que solo tiene fichero. */
    static final String FROM_FILE = "FILE";

    /** Cómo presenta la empresa sus facturas. */
    public enum Mode { PROPIO, VERIFACTI, B2BROUTER }

    /**
     * Lo que dice el fichero de una empresa. {@code problem} explica, en palabras del usuario, por qué no se
     * puede usar; si es nulo, el perfil está completo para su modo.
     */
    public record Profile(String taxId, Path file, Mode mode, VerifactuEnvironment environment,
                          Path certificate, String password, boolean seal, FacturaEIni ini, String problem) {
        public boolean usable() { return problem == null; }

        /** El certificado en Base64, que es como lo recibe el transporte de la AEAT. */
        String certificateBase64() {
            try {
                return Base64.getEncoder().encodeToString(Files.readAllBytes(certificate));
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer el certificado " + certificate.getFileName(), e);
            }
        }
    }

    private final Path directory;
    private final JdbcTemplate jdbc;

    public FiscalProfileService(@Value("${pera.fiscal.config-dir:config/empresas}") Path directory, JdbcTemplate jdbc) {
        this.directory = directory;
        this.jdbc = jdbc;
    }

    /** Perfil de la empresa a partir del NIF de su emisor Veri*Factu; vacío si no tiene fichero. */
    public Optional<Profile> forCompany(UUID companyId) {
        return jdbc.queryForList("SELECT issuer_tax_id FROM verifactu_settings WHERE company_id = ?", String.class, companyId)
                .stream().findFirst().flatMap(this::forTaxId);
    }

    public Optional<Profile> forTaxId(String taxId) {
        String nif = normalize(taxId);
        if (nif.isEmpty()) {
            return Optional.empty();
        }
        Path folder = directory.resolve(nif);
        Path file = folder.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(read(nif, folder, file, FacturaEIni.parse(Files.readAllBytes(file))));
        } catch (IOException e) {
            return Optional.of(new Profile(nif, file, null, null, null, "", false, null,
                    "No se pudo leer " + FILE_NAME + ": " + e.getMessage()));
        }
    }

    private static Profile read(String nif, Path folder, Path file, FacturaEIni ini) {
        String rawMode = ini.get("FACTURA_E", "modo").toUpperCase(Locale.ROOT);
        Mode mode;
        try {
            mode = rawMode.isEmpty() ? Mode.PROPIO : Mode.valueOf(rawMode.replace(" ", ""));
        } catch (IllegalArgumentException e) {
            return new Profile(nif, file, null, null, null, "", false, ini,
                    "El modo «" + rawMode + "» no existe. Usa PROPIO, VERIFACTI o B2BROUTER.");
        }
        // Como en Dimpro, modotest = "1" son pruebas. Sin la clave, pruebas: producción hay que pedirla.
        String test = ini.get("FACTURA_E", "modotest");
        VerifactuEnvironment environment = "0".equals(test) ? VerifactuEnvironment.PRODUCTION : VerifactuEnvironment.TEST;
        if (mode != Mode.PROPIO) {
            return new Profile(nif, file, mode, environment, null, "", false, ini,
                    "La presentación por " + mode + " aún no está disponible en PERA; de momento no se remite nada.");
        }
        String certificate = ini.get("AEAT", "certificado");
        if (certificate.isEmpty()) {
            return new Profile(nif, file, mode, environment, null, "", false, ini,
                    "Falta «certificado» en la sección [AEAT].");
        }
        Path certificatePath = folder.resolve(certificate).normalize();
        if (!certificatePath.startsWith(folder.normalize())) {
            return new Profile(nif, file, mode, environment, null, "", false, ini,
                    "El certificado tiene que estar en la carpeta de la empresa.");
        }
        if (!Files.isRegularFile(certificatePath)) {
            return new Profile(nif, file, mode, environment, certificatePath, "", false, ini,
                    "No está el certificado " + certificate + " en la carpeta de la empresa.");
        }
        boolean seal = "SELLO".equalsIgnoreCase(ini.get("AEAT", "tipo"));
        return new Profile(nif, file, mode, environment, certificatePath, ini.get("AEAT", "clave"), seal, ini, null);
    }

    /**
     * Empresas que remiten con su fichero en modo PROPIO. Les crea la fila de conexión si no la tienen
     * (ahí se lleva el tiempo de espera de la AEAT), sin credenciales: el certificado se lee del fichero en
     * cada envío. Nunca toca una conexión cargada a mano: si se quita el fichero, todo vuelve a como estaba.
     * Si dos empresas tienen el mismo NIF, no se sabe de cuál es el fichero y no se usa para ninguna.
     */
    public Set<UUID> sync() {
        List<Map<String, Object>> companies = jdbc.queryForList("SELECT company_id, issuer_tax_id FROM verifactu_settings");
        Map<String, List<UUID>> byTaxId = new HashMap<>();
        for (Map<String, Object> row : companies) {
            byTaxId.computeIfAbsent(normalize((String) row.get("issuer_tax_id")), ignored -> new ArrayList<>())
                    .add((UUID) row.get("company_id"));
        }
        Set<UUID> withFile = new HashSet<>();
        byTaxId.forEach((taxId, ids) -> {
            Optional<Profile> profile = forTaxId(taxId);
            if (profile.isEmpty() || profile.get().mode() != Mode.PROPIO) {
                return;
            }
            if (ids.size() > 1) {
                log.warn("Hay varias empresas con el NIF {}: no se sabe de cuál es su {}", taxId, FILE_NAME);
                return;
            }
            UUID companyId = ids.getFirst();
            jdbc.update("""
                    INSERT INTO fiscal_connections(company_id, provider, account, secret_cipher, enabled)
                    VALUES (?, 'AEAT', '', ?, false) ON CONFLICT (company_id, provider) DO NOTHING
                    """, companyId, FROM_FILE);
            withFile.add(companyId);
        });
        return withFile;
    }

    static String normalize(String taxId) {
        return taxId == null ? "" : taxId.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
