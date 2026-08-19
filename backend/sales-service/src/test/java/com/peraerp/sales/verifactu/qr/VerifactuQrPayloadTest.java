package com.peraerp.sales.verifactu.qr;

import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contenido del QR de cotejo.
 *
 * <p>Si estos valores no coinciden exactamente con los del registro remitido, el receptor escanea
 * el QR y la AEAT le dice que esa factura no consta. El fallo aparece en manos del cliente, no en
 * las nuestras, así que el formato se fija aquí.</p>
 */
class VerifactuQrPayloadTest {

    @Test
    void matchesTheOfficialExample() {
        String payload = VerifactuQrPayload.of(VerifactuEnvironment.TEST, "89890001K", "12345678/G33",
                LocalDate.of(2024, 1, 1), new BigDecimal("241.40"));

        assertThat(payload).isEqualTo("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR"
                + "?nif=89890001K&numserie=12345678/G33&fecha=01-01-2024&importe=241.40");
    }

    @Test
    void productionUsesTheProductionService() {
        String payload = VerifactuQrPayload.of(VerifactuEnvironment.PRODUCTION, "89890001K", "FAC-2026-000001",
                LocalDate.of(2026, 8, 19), new BigDecimal("242.00"));

        assertThat(payload).startsWith("https://www2.agenciatributaria.es/wlpl/TIKE-CONT/ValidarQR?");
    }

    @Test
    void parametersKeepTheOrderRequiredByTheSpecification() {
        String payload = VerifactuQrPayload.of(VerifactuEnvironment.TEST, "89890001K", "FAC-1",
                LocalDate.of(2026, 8, 19), new BigDecimal("242.00"));

        assertThat(payload.indexOf("nif=")).isLessThan(payload.indexOf("numserie="));
        assertThat(payload.indexOf("numserie=")).isLessThan(payload.indexOf("fecha="));
        assertThat(payload.indexOf("fecha=")).isLessThan(payload.indexOf("importe="));
    }

    @Test
    void dateAndAmountUseTheSameFormatAsTheFingerprint() {
        String payload = VerifactuQrPayload.of(VerifactuEnvironment.TEST, "89890001K", "FAC-1",
                LocalDate.of(2026, 8, 3), new BigDecimal("25052.0000"));

        assertThat(payload).contains("fecha=03-08-2026");
        assertThat(payload).contains("importe=25052.00");
    }

    @Test
    void slashesInTheInvoiceNumberAreLeftAsTheSpecificationShowsThem() {
        String payload = VerifactuQrPayload.of(VerifactuEnvironment.TEST, "89890001K", "A/2026/1",
                LocalDate.of(2026, 8, 19), BigDecimal.TEN);

        assertThat(payload).contains("numserie=A/2026/1");
    }

    @Test
    void charactersThatWouldBreakTheQueryStringArePercentEncoded() {
        String payload = VerifactuQrPayload.of(VerifactuEnvironment.TEST, "89890001K", "FAC 1&2",
                LocalDate.of(2026, 8, 19), BigDecimal.TEN);

        assertThat(payload).contains("numserie=FAC%201%262");
        assertThat(payload).doesNotContain("numserie=FAC+1");
    }

    @Test
    void accentedCharactersAreEncodedAsUtf8() {
        String payload = VerifactuQrPayload.of(VerifactuEnvironment.TEST, "89890001K", "AÑO-1",
                LocalDate.of(2026, 8, 19), BigDecimal.TEN);

        assertThat(payload).contains("numserie=A%C3%91O-1");
    }
}
