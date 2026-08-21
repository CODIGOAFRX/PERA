package com.peraerp.masterdata.party;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dígito de control del identificador fiscal español.
 *
 * <p>La AEAT rechaza un registro con el NIF mal formado, y ese rechazo llega días después de
 * emitir la factura. Cazarlo en el formulario convierte un problema fiscal en un mensaje de
 * validación.</p>
 */
class SpanishTaxIdValidatorTest {

    @Test
    void acceptsTheExampleTaxIdUsedByTheTaxAgency() {
        assertThat(SpanishTaxIdValidator.isValid("89890001K")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678Z", "00000000T", "X1234567L", "Z1234567R", "B75777847", "A58818501"})
    void acceptsWellFormedIdentifiers(String taxId) {
        assertThat(SpanishTaxIdValidator.isValid(taxId)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678A", "X1234567A", "B75777848", "A58818502"})
    void rejectsAWrongControlCharacter(String taxId) {
        assertThat(SpanishTaxIdValidator.isValid(taxId)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"89890001", "89890001KK", "", "   ", "ÑÑÑÑÑÑÑÑÑ", "112345678", "I1234567J"})
    void rejectsMalformedInput(String taxId) {
        assertThat(SpanishTaxIdValidator.isValid(taxId)).isFalse();
    }

    @Test
    void nullIsNotValid() {
        assertThat(SpanishTaxIdValidator.isValid(null)).isFalse();
    }

    @Test
    void normalisesCaseSpacesAndDashesBeforeChecking() {
        assertThat(SpanishTaxIdValidator.isValid("  89890001k  ")).isTrue();
        assertThat(SpanishTaxIdValidator.isValid("8989-0001-K")).isTrue();
    }

    @Test
    void organisationsWhoseControlMustBeALetterRejectADigit() {
        // Las formas K, P, Q, S, N y W llevan siempre letra de control.
        assertThat(SpanishTaxIdValidator.isValid("P0000000J")).isTrue();
        assertThat(SpanishTaxIdValidator.isValid("P00000000")).isFalse();
    }

    @Test
    void organisationsWhoseControlMustBeADigitRejectALetter() {
        // Las formas A, B, E y H llevan siempre dígito de control.
        assertThat(SpanishTaxIdValidator.isValid("B00000000")).isTrue();
        assertThat(SpanishTaxIdValidator.isValid("B0000000J")).isFalse();
    }

    @Test
    void checksTheControlCharacterNotWhetherTheIdentifierExists() {
        // B00000000 no está asignado a nadie, pero el dígito de control cuadra. Esta clase detecta
        // erratas, no consulta el censo.
        assertThat(SpanishTaxIdValidator.isValid("B00000000")).isTrue();
    }
}
