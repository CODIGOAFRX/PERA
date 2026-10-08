package com.peraerp.sales.integration;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.mail.MailSecretCipher;
import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import com.peraerp.sales.verifactu.domain.VerifactuState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Remisión de los registros Veri*Factu a la AEAT, en pruebas o en producción según la empresa.
 *
 * <p>Como hace DimproCristalWin, cada factura expedida se presenta sin que nadie lo pida: el trabajo
 * lo lanza {@link VerifactuRemissionWorker}. A diferencia de Dimpro, no hay intermediario: el registro,
 * la huella y la cadena son de PERA y se remiten con el certificado de la empresa.</p>
 *
 * <p>Reglas que garantizan que no se pierde ni se duplica nada:</p>
 * <ul>
 *   <li>Los registros se reclaman y se marcan como remitidos en una transacción que se confirma
 *       <em>antes</em> de la llamada HTTP. Dos procesos no pueden remitir el mismo lote.</li>
 *   <li>Si la respuesta no llega, el registro sigue «remitido» y se vuelve a enviar pasado un rato. Si la
 *       AEAT ya lo tenía, contesta «duplicado» con su estado, y así se cierra.</li>
 *   <li>Si la AEAT rechaza el envío entero (fallo SOAP), no ha registrado nada: los registros vuelven a
 *       pendientes y se reintenta con esperas crecientes.</li>
 *   <li>Entre envíos se respeta el tiempo de espera que fija la AEAT en cada respuesta.</li>
 *   <li>Un registro rechazado no se reenvía: su huella ya está encadenada y corregirlo exige un registro
 *       nuevo de subsanación.</li>
 * </ul>
 */
@Service
public class VerifactuRemissionService {
    private static final Logger log = LoggerFactory.getLogger(VerifactuRemissionService.class);
    private static final DateTimeFormatter AEAT_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    /** Un registro remitido sin respuesta se vuelve a enviar pasado este tiempo, para reconciliarlo. */
    static final int RECONCILE_AFTER_SECONDS = 120;
    /** Espera mínima entre envíos cuando la AEAT no indica otra. */
    static final int DEFAULT_WAIT_SECONDS = 60;
    /** Tope de la espera tras fallos seguidos. */
    static final int MAX_BACKOFF_SECONDS = 3600;

    /** Resultado de un intento de remisión de una empresa. */
    public record Outcome(int remitted, String message) {}

    /** Lo reclamado para un envío: credenciales, entorno y registros ya marcados como remitidos. */
    record Claim(String certificate, String password, boolean seal, VerifactuEnvironment environment,
                 List<VerifactuRecord> records, String message) {
        static Claim nothing(String message) { return new Claim(null, null, false, null, List.of(), message); }
    }

    private final JdbcTemplate jdbc;
    private final MailSecretCipher cipher;
    private final ObjectMapper mapper;
    private final AeatTestTransport transport;
    private final VerifactuSettingsRepository settings;
    private final VerifactuRecordRepository records;
    private final TransactionTemplate tx;

    public VerifactuRemissionService(JdbcTemplate jdbc, MailSecretCipher cipher, ObjectMapper mapper,
                                     AeatTestTransport transport, VerifactuSettingsRepository settings,
                                     VerifactuRecordRepository records, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.mapper = mapper;
        this.transport = transport;
        this.settings = settings;
        this.records = records;
        this.tx = new TransactionTemplate(transactions);
    }

    /** Empresas con Veri*Factu y conexión activos, con algo por remitir y fuera del tiempo de espera. */
    public List<UUID> dueCompanies() {
        return jdbc.queryForList("""
                SELECT c.company_id FROM fiscal_connections c
                JOIN verifactu_settings s ON s.company_id = c.company_id
                WHERE c.provider = 'AEAT' AND c.enabled AND s.enabled
                  AND (c.next_send_at IS NULL OR c.next_send_at <= now())
                  AND EXISTS (SELECT 1 FROM verifactu_records r WHERE r.company_id = c.company_id
                      AND r.record_type = 'ALTA' AND (r.state = 'PENDING'
                      OR (r.state = 'SENT' AND r.last_attempt_at < now() - make_interval(secs => ?))))
                """, UUID.class, RECONCILE_AFTER_SECONDS);
    }

    /** Remite lo pendiente de una empresa, si le toca. Nunca lanza por un fallo de la AEAT: lo anota. */
    public Outcome remit(UUID companyId) {
        Claim claim = tx.execute(status -> claim(companyId));
        if (claim == null || claim.records().isEmpty()) {
            return new Outcome(0, claim == null ? "" : claim.message());
        }
        AeatTestTransport.BatchResult result;
        try {
            result = transport.sendBatch(claim.records(), claim.certificate(), claim.password(), claim.seal(),
                    claim.environment());
        } catch (AeatTestTransport.EnvelopeRejected rejected) {
            // Nada quedó registrado: los registros vuelven a la cola y se espacia el siguiente intento.
            tx.executeWithoutResult(status -> envelopeFailed(companyId, claim.records(), rejected.getMessage(), true));
            return new Outcome(0, rejected.getMessage());
        } catch (Exception uncertain) {
            log.warn("Remisión Veri*Factu sin respuesta para la empresa {}: {}", companyId, uncertain.getMessage());
            String message = "No llegó respuesta de la AEAT. Se volverá a remitir para confirmar el estado; "
                    + "si ya lo tenía, contestará con su resultado.";
            tx.executeWithoutResult(status -> envelopeFailed(companyId, claim.records(), message, false));
            return new Outcome(0, message);
        }
        tx.executeWithoutResult(status -> apply(companyId, claim.records(), result));
        return new Outcome(claim.records().size(), "Remitidos " + claim.records().size() + " registros.");
    }

    /**
     * Remite ya lo pendiente de la empresa que incluye este registro. Es el botón «Enviar ahora»: no se
     * salta el tiempo de espera de la AEAT, solo adelanta el trabajo automático.
     */
    public Outcome remitNow(UUID companyId, UUID recordId) {
        VerifactuRecord record = records.findByIdAndCompanyId(recordId, companyId)
                .orElseThrow(() -> new BusinessRuleException("Registro no encontrado"));
        if (record.getState() != VerifactuState.PENDING && record.getState() != VerifactuState.SENT) {
            throw new BusinessRuleException("Este registro ya tiene respuesta de la AEAT.");
        }
        return remit(companyId);
    }

    private Claim claim(UUID companyId) {
        var connections = jdbc.query("""
                SELECT secret_cipher, account, enabled, next_send_at IS NULL OR next_send_at <= now()
                FROM fiscal_connections WHERE company_id = ? AND provider = 'AEAT' FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Object[]{rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4)},
                companyId);
        if (connections.isEmpty()) {
            return Claim.nothing("No hay conexión con la AEAT o la está usando otro envío.");
        }
        Object[] connection = connections.getFirst();
        if (!(Boolean) connection[2]) {
            return Claim.nothing("La conexión con la AEAT está desactivada.");
        }
        if (!(Boolean) connection[3]) {
            return Claim.nothing("La AEAT fija un tiempo de espera entre envíos. "
                    + "Se remitirá automáticamente en cuanto termine.");
        }
        VerifactuSettings configuration = settings.findByCompanyId(companyId).orElse(null);
        if (configuration == null || !configuration.isEnabled()) {
            return Claim.nothing("Veri*Factu está desactivado en la empresa.");
        }
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM verifactu_records WHERE company_id = ? AND record_type = 'ALTA'
                  AND (state = 'PENDING' OR (state = 'SENT' AND last_attempt_at < now() - make_interval(secs => ?)))
                ORDER BY sequence_number LIMIT ? FOR UPDATE SKIP LOCKED
                """, UUID.class, companyId, RECONCILE_AFTER_SECONDS, AeatTestTransport.MAX_BATCH);
        if (ids.isEmpty()) {
            return Claim.nothing("No hay registros pendientes de remitir.");
        }
        var candidates = new ArrayList<>(records.findAllById(ids));
        candidates.sort(Comparator.comparingLong(VerifactuRecord::getSequenceNumber));
        // Una cabecera por envío: si la empresa cambió de NIF, los del NIF anterior van en un envío propio.
        String issuer = candidates.getFirst().getIssuerTaxId();
        var batch = new ArrayList<VerifactuRecord>();
        for (VerifactuRecord candidate : candidates) {
            if (!issuer.equals(candidate.getIssuerTaxId())) {
                continue;
            }
            String invalid = schemaError(candidate);
            if (invalid != null) {
                // No bloquea a los demás: se queda pendiente con el motivo a la vista.
                note(candidate.getId(), companyId, null, invalid);
                continue;
            }
            batch.add(candidate);
        }
        if (batch.isEmpty()) {
            return Claim.nothing("Los registros pendientes no superan el esquema oficial de la AEAT.");
        }
        var secret = mapper.readTree(cipher.decrypt(companyId, (String) connection[0]));
        if (!"AEAT".equals(secret.path("provider").asText())) {
            throw new BusinessRuleException("Credencial de otro proveedor");
        }
        List<UUID> batchIds = batch.stream().map(VerifactuRecord::getId).toList();
        for (UUID id : batchIds) {
            jdbc.update("""
                    UPDATE verifactu_records SET state = 'SENT', attempt_count = attempt_count + 1,
                        last_attempt_at = now(), version = version + 1 WHERE id = ? AND company_id = ?
                    """, id, companyId);
        }
        // Mientras dura el envío nadie más remite por esta empresa.
        jdbc.update("UPDATE fiscal_connections SET next_send_at = now() + make_interval(secs => ?) "
                + "WHERE company_id = ? AND provider = 'AEAT'", DEFAULT_WAIT_SECONDS, companyId);
        return new Claim(secret.path("certificate").asText(), secret.path("password").asText(),
                "SEAL".equals(connection[1]), configuration.getEnvironment(), batch, "");
    }

    private void apply(UUID companyId, List<VerifactuRecord> batch, AeatTestTransport.BatchResult result) {
        Map<String, AeatTestTransport.LineResult> byInvoice = new HashMap<>();
        for (AeatTestTransport.LineResult line : result.lines()) {
            byInvoice.put(line.number() + "|" + line.date(), line);
        }
        for (VerifactuRecord record : batch) {
            var line = byInvoice.get(record.getInvoiceNumber() + "|" + record.getInvoiceDate().format(AEAT_DATE));
            if (line == null) {
                note(record.getId(), companyId, null,
                        "La AEAT no devolvió resultado para este registro. Se volverá a remitir para confirmarlo.");
            } else if ("UNKNOWN".equals(line.state())) {
                note(record.getId(), companyId, line.errorCode(), line.message()
                        + ". No consta su estado; se volverá a remitir para confirmarlo.");
            } else {
                jdbc.update("""
                        UPDATE verifactu_records SET state = ?, aeat_csv = ?, aeat_response = ?, aeat_error_code = ?,
                            aeat_message = ?, version = version + 1 WHERE id = ? AND company_id = ? AND state = 'SENT'
                        """, line.state(), blankToNull(result.csv()), line.response(), line.errorCode(), line.message(),
                        record.getId(), companyId);
            }
        }
        jdbc.update("""
                UPDATE fiscal_connections SET next_send_at = now() + make_interval(secs => ?), failures = 0,
                    last_error = NULL, last_sent_at = now() WHERE company_id = ? AND provider = 'AEAT'
                """, Math.max(DEFAULT_WAIT_SECONDS, result.waitSeconds()), companyId);
    }

    private void envelopeFailed(UUID companyId, List<VerifactuRecord> batch, String message, boolean certain) {
        for (VerifactuRecord record : batch) {
            if (certain) {
                jdbc.update("""
                        UPDATE verifactu_records SET state = 'PENDING', aeat_message = ?, version = version + 1
                        WHERE id = ? AND company_id = ? AND state = 'SENT'
                        """, message, record.getId(), companyId);
            } else {
                note(record.getId(), companyId, null, message);
            }
        }
        Integer failures = jdbc.queryForObject("UPDATE fiscal_connections SET failures = failures + 1 "
                + "WHERE company_id = ? AND provider = 'AEAT' RETURNING failures", Integer.class, companyId);
        jdbc.update("UPDATE fiscal_connections SET next_send_at = now() + make_interval(secs => ?), last_error = ? "
                + "WHERE company_id = ? AND provider = 'AEAT'", backoff(failures == null ? 1 : failures), message, companyId);
    }

    private void note(UUID recordId, UUID companyId, String code, String message) {
        jdbc.update("UPDATE verifactu_records SET aeat_error_code = ?, aeat_message = ?, version = version + 1 "
                + "WHERE id = ? AND company_id = ?", code, AeatTestTransport.abbreviate(message, 1500), recordId, companyId);
    }

    private static String schemaError(VerifactuRecord record) {
        try {
            AeatSchema.validate(AeatTestTransport.envelope(record.getPayloadXml()));
            return null;
        } catch (Exception e) {
            return AeatTestTransport.abbreviate("El registro no supera el esquema oficial de la AEAT: " + e.getMessage(), 1500);
        }
    }

    /** 1 minuto, 2, 4, 8... hasta una hora. */
    static int backoff(int failures) {
        long seconds = (long) DEFAULT_WAIT_SECONDS << Math.min(Math.max(failures - 1, 0), 10);
        return (int) Math.min(seconds, MAX_BACKOFF_SECONDS);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Momento a partir del cual se puede volver a remitir, para enseñarlo en pantalla. */
    public Instant nextSendAt(UUID companyId) {
        return jdbc.query("SELECT next_send_at FROM fiscal_connections WHERE company_id = ? AND provider = 'AEAT'",
                (rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), companyId)
                .stream().findFirst().orElse(null);
    }
}
