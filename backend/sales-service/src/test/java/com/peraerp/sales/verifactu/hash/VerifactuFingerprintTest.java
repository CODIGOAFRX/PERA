package com.peraerp.sales.verifactu.hash;

import com.peraerp.sales.verifactu.domain.InvoiceKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La huella es el punto donde un error de formato invisible se convierte en un rechazo de la
 * AEAT factura a factura. Estas pruebas fijan el formato exacto y deben fallar ante cualquier
 * cambio, por inocente que parezca.
 */
class VerifactuFingerprintTest {

    private static final String OFFICIAL_INPUT =
            "IDEmisorFactura=89890001K&NumSerieFactura=12345678/G33&FechaExpedicionFactura=01-01-2024"
            + "&TipoFactura=F1&CuotaTotal=12.35&ImporteTotal=123.45&Huella="
            + "&FechaHoraHusoGenRegistro=2024-01-01T19:20:30+01:00";

    private static final String OFFICIAL_FINGERPRINT =
            "3C464DAF61ACB827C65FDA19F352A4E3BDC2C640E9E9FC4CC058073F38F12F60";

    private FingerprintInput officialAlta() {
        return FingerprintInput.forAlta("89890001K", "12345678/G33", LocalDate.of(2024, 1, 1),
                InvoiceKind.F1, new BigDecimal("12.35"), new BigDecimal("123.45"), null,
                ZonedDateTime.of(2024, 1, 1, 19, 20, 30, 0, ZoneOffset.ofHours(1)));
    }

    @Test
    void altaMatchesOfficialExampleInput() {
        assertThat(officialAlta().asString()).isEqualTo(OFFICIAL_INPUT);
    }

    @Test
    void altaMatchesOfficialExampleFingerprint() {
        assertThat(RecordFingerprint.of(officialAlta())).isEqualTo(OFFICIAL_FINGERPRINT);
    }

    @Test
    void firstRecordOfChainLeavesPreviousFingerprintEmpty() {
        assertThat(officialAlta().asString()).contains("&Huella=&");
    }

    @Test
    void anulacionUsesItsOwnFieldNames() {
        FingerprintInput input = FingerprintInput.forAnulacion("89890001K", "12345678/G33",
                LocalDate.of(2024, 1, 1), OFFICIAL_FINGERPRINT,
                ZonedDateTime.of(2024, 1, 3, 9, 0, 0, 0, ZoneOffset.ofHours(1)));

        assertThat(input.asString()).isEqualTo(
                "IDEmisorFacturaAnulada=89890001K&NumSerieFacturaAnulada=12345678/G33"
                + "&FechaExpedicionFacturaAnulada=01-01-2024&Huella=" + OFFICIAL_FINGERPRINT
                + "&FechaHoraHusoGenRegistro=2024-01-03T09:00:00+01:00");
    }

    @Test
    void chainedRecordEmbedsPreviousFingerprint() {
        String previous = RecordFingerprint.of(officialAlta());

        FingerprintInput next = FingerprintInput.forAlta("89890001K", "12345678/G34",
                LocalDate.of(2024, 1, 2), InvoiceKind.F1, new BigDecimal("21.00"),
                new BigDecimal("121.00"), previous,
                ZonedDateTime.of(2024, 1, 2, 10, 0, 0, 0, ZoneOffset.ofHours(1)));

        assertThat(next.asString()).contains("&Huella=" + previous + "&");
        assertThat(RecordFingerprint.of(next)).isNotEqualTo(previous);
    }

    @Test
    void oneCentDifferenceBreaksTheFingerprint() {
        String previous = RecordFingerprint.of(officialAlta());

        String withTotal = RecordFingerprint.of(FingerprintInput.forAlta("89890001K", "A-1",
                LocalDate.of(2024, 1, 2), InvoiceKind.F1, new BigDecimal("21.00"),
                new BigDecimal("121.00"), previous,
                ZonedDateTime.of(2024, 1, 2, 10, 0, 0, 0, ZoneOffset.ofHours(1))));
        String withOneCentMore = RecordFingerprint.of(FingerprintInput.forAlta("89890001K", "A-1",
                LocalDate.of(2024, 1, 2), InvoiceKind.F1, new BigDecimal("21.00"),
                new BigDecimal("121.01"), previous,
                ZonedDateTime.of(2024, 1, 2, 10, 0, 0, 0, ZoneOffset.ofHours(1))));

        assertThat(withTotal).isNotEqualTo(withOneCentMore);
    }

    @Test
    void fingerprintIsUppercaseHexOfSixtyFourCharacters() {
        String fingerprint = RecordFingerprint.of(officialAlta());

        assertThat(fingerprint).hasSize(RecordFingerprint.LENGTH);
        assertThat(fingerprint).matches("[0-9A-F]{64}");
    }

    @Test
    void amountsAlwaysCarryTwoDecimalsWithoutGrouping() {
        assertThat(VerifactuFieldFormat.amount(new BigDecimal("241.4000"))).isEqualTo("241.40");
        assertThat(VerifactuFieldFormat.amount(new BigDecimal("25052.00"))).isEqualTo("25052.00");
        assertThat(VerifactuFieldFormat.amount(new BigDecimal("-100.5"))).isEqualTo("-100.50");
        assertThat(VerifactuFieldFormat.amount(null)).isEmpty();
    }

    @Test
    void amountsRoundHalfUpFromTheFourDecimalScaleUsedBySales() {
        assertThat(VerifactuFieldFormat.amount(new BigDecimal("12.3450"))).isEqualTo("12.35");
        assertThat(VerifactuFieldFormat.amount(new BigDecimal("12.3449"))).isEqualTo("12.34");
    }

    @Test
    void issueDateUsesDayMonthYear() {
        assertThat(VerifactuFieldFormat.date(LocalDate.of(2026, 8, 3))).isEqualTo("03-08-2026");
        assertThat(VerifactuFieldFormat.date(null)).isEmpty();
    }

    @Test
    void generationTimestampDropsMillisecondsAndKeepsTheOffset() {
        ZonedDateTime withMillis = ZonedDateTime.of(2026, 8, 3, 11, 38, 11, 197_000_000,
                ZoneId.of("Europe/Madrid"));

        assertThat(VerifactuFieldFormat.timestamp(withMillis)).isEqualTo("2026-08-03T11:38:11+02:00");
    }

    @Test
    void generationTimestampKeepsWinterOffsetForTheSameZone() {
        ZonedDateTime winter = ZonedDateTime.of(2026, 1, 15, 8, 0, 0, 0, ZoneId.of("Europe/Madrid"));

        assertThat(VerifactuFieldFormat.timestamp(winter)).isEqualTo("2026-01-15T08:00:00+01:00");
    }

    @Test
    void valuesAreTrimmedBeforeHashing() {
        FingerprintInput padded = FingerprintInput.forAlta("  89890001K ", " 12345678/G33  ",
                LocalDate.of(2024, 1, 1), InvoiceKind.F1, new BigDecimal("12.35"),
                new BigDecimal("123.45"), null,
                ZonedDateTime.of(2024, 1, 1, 19, 20, 30, 0, ZoneOffset.ofHours(1)));

        assertThat(RecordFingerprint.of(padded)).isEqualTo(OFFICIAL_FINGERPRINT);
    }
}
