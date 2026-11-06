package com.peraerp.sales.integration;

import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FiscalProfileServiceTest {
    @TempDir Path root;
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private Path company(String nif, String ini) throws Exception {
        Path folder = Files.createDirectories(root.resolve(nif));
        Files.write(folder.resolve("factura_e.ini"), ini.getBytes(Charset.forName("windows-1252")));
        return folder;
    }

    @Test
    void readsDimproStyleFilesInAnsiWithCommentsQuotesAndUnderscoredSections() {
        var ini = FacturaEIni.parse("""
                [FACTURA_E]
                carpeta_salida = C:\\Facturas\\Salida
                modotest = "1"
                risp = "Inversión del sujeto pasivo"
                [VERI_FACTI]
                *apikey_test = antigua
                apikey_test = nueva
                """.getBytes(Charset.forName("windows-1252")));
        assertThat(ini.get("factura_e", "modotest")).isEqualTo("1");
        assertThat(ini.get("FACTURA_E", "risp")).isEqualTo("Inversión del sujeto pasivo");
        assertThat(ini.get("FACTURA_E", "carpeta_salida")).isEqualTo("C:\\Facturas\\Salida");
        // [VERI_FACTI] se lee como [VERIFACTI]: en Dimpro ese error deja la sección vacía sin avisar.
        assertThat(ini.get("VERIFACTI", "apikey_test")).isEqualTo("nueva");
        assertThat(ini.get("VERIFACTI", "url_crea")).isEmpty();
    }

    @Test
    void readsUtf8WithBom() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "[FACTURA_E]\nrisp = Operación\n".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        assertThat(FacturaEIni.parse(all).get("FACTURA_E", "risp")).isEqualTo("Operación");
    }

    @Test
    void ownSubmissionUsesTheCertificateOfTheCompanyFolder() throws Exception {
        Path folder = company("B12345678", """
                [FACTURA_E]
                modo = PROPIO
                modotest = "1"
                [AEAT]
                certificado = empresa.p12
                clave = secreta
                tipo = SELLO
                """);
        Files.write(folder.resolve("empresa.p12"), new byte[]{1, 2, 3});
        var profile = new FiscalProfileService(root, jdbc).forTaxId("b-12345678").orElseThrow();
        assertThat(profile.usable()).isTrue();
        assertThat(profile.mode()).isEqualTo(FiscalProfileService.Mode.PROPIO);
        assertThat(profile.environment()).isEqualTo(VerifactuEnvironment.TEST);
        assertThat(profile.seal()).isTrue();
        assertThat(profile.password()).isEqualTo("secreta");
        assertThat(profile.certificateBase64()).isEqualTo("AQID");
    }

    @Test
    void productionOnlyWhenTheFileSaysModotestZero() throws Exception {
        company("B12345678", "[FACTURA_E]\nmodotest = \"0\"\n[AEAT]\ncertificado = x.p12\n");
        var profile = new FiscalProfileService(root, jdbc).forTaxId("B12345678").orElseThrow();
        assertThat(profile.environment()).isEqualTo(VerifactuEnvironment.PRODUCTION);
        // Sin el certificado en la carpeta no se puede usar, y se dice por qué.
        assertThat(profile.problem()).contains("x.p12");
    }

    @Test
    void certificateCannotPointOutsideTheCompanyFolder() throws Exception {
        company("B12345678", "[AEAT]\ncertificado = ../../otro.p12\n");
        var profile = new FiscalProfileService(root, jdbc).forTaxId("B12345678").orElseThrow();
        assertThat(profile.usable()).isFalse();
    }

    @Test
    void providerModesAreRecognisedButNotYetSubmitted() throws Exception {
        company("B12345678", "[FACTURA_E]\nmodo = VERIFACTI\n[VERIFACTI]\napikey_test = k\n");
        var profile = new FiscalProfileService(root, jdbc).forTaxId("B12345678").orElseThrow();
        assertThat(profile.mode()).isEqualTo(FiscalProfileService.Mode.VERIFACTI);
        assertThat(profile.usable()).isFalse();
    }

    @Test
    void companyWithoutFileHasNoProfile() {
        assertThat(new FiscalProfileService(root, jdbc).forTaxId("B99999999")).isEmpty();
    }

    private static Map<String, Object> row(UUID company, String nif) {
        var row = new HashMap<String, Object>();
        row.put("company_id", company);
        row.put("issuer_tax_id", nif);
        return row;
    }

    @Test
    void syncOnlyCreatesTheMissingConnectionRowAndNeverTouchesStoredCredentials() throws Exception {
        Path folder = company("B12345678", "[AEAT]\ncertificado = c.p12\n");
        Files.write(folder.resolve("c.p12"), new byte[]{1});
        company("B22222222", "[FACTURA_E]\nmodo = VERIFACTI\n");
        UUID withFile = UUID.randomUUID();
        UUID manual = UUID.randomUUID();
        UUID provider = UUID.randomUUID();
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                row(withFile, "B12345678"), row(manual, "B87654321"), row(provider, "B22222222")));
        var result = new FiscalProfileService(root, jdbc).sync();
        assertThat(result).containsExactly(withFile);
        verify(jdbc).update(contains("ON CONFLICT (company_id, provider) DO NOTHING"), eq(withFile), eq("FILE"));
        // Ni la empresa sin fichero ni la que presenta por proveedor reciben nada.
        verify(jdbc, never()).update(anyString(), eq(manual), any());
        verify(jdbc, never()).update(anyString(), eq(provider), any());
    }

    @Test
    void twoCompaniesWithTheSameTaxIdDoNotUseTheFile() throws Exception {
        Path folder = company("B12345678", "[AEAT]\ncertificado = c.p12\n");
        Files.write(folder.resolve("c.p12"), new byte[]{1});
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                row(UUID.randomUUID(), "B12345678"), row(UUID.randomUUID(), "b12345678")));
        assertThat(new FiscalProfileService(root, jdbc).sync()).isEmpty();
    }
}
