package com.peraerp.masterdata.party;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalización del identificador fiscal del tercero.
 *
 * <p>Sin tipo de identificación no se puede construir el bloque {@code IDDestinatario} de un
 * registro de facturación, así que un tercero con NIF nunca debe quedar sin clasificar.</p>
 */
class PartyTaxIdentificationTest {

    private static final UUID COMPANY = UUID.randomUUID();

    private Party party(String taxId, TaxIdentificationType type, String country) {
        return new Party(COMPANY, "C001", "ALUMINIOS FAMA S.L.", null, taxId, type, country,
                null, null, null);
    }

    @Test
    void taxIdWithoutTypeIsAssumedToBeASpanishNif() {
        Party party = party("B75777847", null, null);

        assertThat(party.getTaxIdentificationType()).isEqualTo(TaxIdentificationType.NIF);
        assertThat(party.getTaxCountryCode()).isEqualTo("ES");
    }

    @Test
    void legacyConstructorStillClassifiesTheTaxId() {
        Party party = new Party(COMPANY, "C001", "ALUMINIOS FAMA S.L.", null, "B75777847", null, null, null);

        assertThat(party.getTaxIdentificationType()).isEqualTo(TaxIdentificationType.NIF);
        assertThat(party.getTaxCountryCode()).isEqualTo("ES");
    }

    @Test
    void foreignIdentificationKeepsItsTypeAndCountry() {
        Party party = party("FR40303265045", TaxIdentificationType.VAT_NUMBER, "fr");

        assertThat(party.getTaxIdentificationType()).isEqualTo(TaxIdentificationType.VAT_NUMBER);
        assertThat(party.getTaxCountryCode()).isEqualTo("FR");
    }

    @Test
    void withoutTaxIdThereIsNoTypeAndNoCountry() {
        Party party = party("   ", TaxIdentificationType.PASSPORT, "PT");

        assertThat(party.getTaxId()).isNull();
        assertThat(party.getTaxIdentificationType()).isNull();
        assertThat(party.getTaxCountryCode()).isNull();
    }

    @Test
    void taxIdIsTrimmedAndUppercased() {
        assertThat(party("  b75777847 ", null, null).getTaxId()).isEqualTo("B75777847");
    }

    @Test
    void updateReclassifiesWhenTheIdentificationChanges() {
        Party party = party("B75777847", null, null);

        party.update("ALUMINIOS FAMA S.L.", null, "FR40303265045", TaxIdentificationType.VAT_NUMBER, "FR",
                null, null, null, true);

        assertThat(party.getTaxIdentificationType()).isEqualTo(TaxIdentificationType.VAT_NUMBER);
        assertThat(party.getTaxCountryCode()).isEqualTo("FR");
    }

    @Test
    void legacyUpdatePreservesTheExistingClassification() {
        Party party = party("FR40303265045", TaxIdentificationType.VAT_NUMBER, "FR");

        party.update("Nuevo nombre", null, "FR40303265045", null, null, null, true);

        assertThat(party.getTaxIdentificationType()).isEqualTo(TaxIdentificationType.VAT_NUMBER);
        assertThat(party.getTaxCountryCode()).isEqualTo("FR");
    }

    @Test
    void onlyForeignTypesCarryAnAeatCode() {
        assertThat(TaxIdentificationType.NIF.code()).isNull();
        assertThat(TaxIdentificationType.NIF.isForeign()).isFalse();
        assertThat(TaxIdentificationType.VAT_NUMBER.code()).isEqualTo("02");
        assertThat(TaxIdentificationType.PASSPORT.code()).isEqualTo("03");
        assertThat(TaxIdentificationType.FOREIGN_OFFICIAL_ID.code()).isEqualTo("04");
        assertThat(TaxIdentificationType.RESIDENCE_CERTIFICATE.code()).isEqualTo("05");
        assertThat(TaxIdentificationType.OTHER_DOCUMENT.code()).isEqualTo("06");
        assertThat(TaxIdentificationType.NOT_REGISTERED.code()).isEqualTo("07");
    }
}
