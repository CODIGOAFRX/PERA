package com.peraerp.masterdata.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Códigos internos EAN-13 con prefijo 20 que PERA asigna a los artículos. */
class InternalBarcodeTest {

    @Test
    void buildsAValidEan13WithThePrefixForInternalUse() {
        // El de la etiqueta de la fiambrera de la demo, leído por la cámara del móvil.
        assertThat(InternalBarcode.ean13(5059)).isEqualTo("2000000050591");
        assertThat(InternalBarcode.ean13(1)).isEqualTo("2000000000015");
        assertThat(InternalBarcode.ean13(9_999_999_999L)).hasSize(13).startsWith("209999999999");
    }

    @Test
    void checkDigitMatchesTheEanRule() {
        // EAN-13 real de un fabricante: 4006381333931.
        assertThat(InternalBarcode.checkDigit("400638133393")).isEqualTo(1);
    }

    @Test
    void rejectsNumbersOutOfRange() {
        assertThatThrownBy(() -> InternalBarcode.ean13(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InternalBarcode.ean13(10_000_000_000L)).isInstanceOf(IllegalArgumentException.class);
    }
}
