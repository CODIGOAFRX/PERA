package com.peraerp.masterdata.catalog;

/**
 * Códigos de barras internos que PERA asigna a los artículos que no traen el EAN del fabricante.
 *
 * <p>Son EAN-13 con el prefijo 20, que GS1 reserva para uso interno de cada empresa: no chocan con
 * ningún producto del mercado y cualquier lector y la cámara del móvil los leen. Llevan diez cifras
 * de número correlativo y el dígito de control.</p>
 */
final class InternalBarcode {

    static final String PREFIX = "20";
    static final String PATTERN = "^20[0-9]{11}$";
    static final long MAX_SEQUENCE = 9_999_999_999L;

    private InternalBarcode() {
    }

    /** EAN-13 interno para el número dado (de 1 a 9.999.999.999). */
    static String ean13(long sequence) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Número de código interno fuera de rango: " + sequence);
        }
        String body = PREFIX + String.format("%010d", sequence);
        return body + checkDigit(body);
    }

    /** Dígito de control EAN: posiciones impares por 1 y pares por 3, contando desde la izquierda. */
    static int checkDigit(String twelveDigits) {
        int sum = 0;
        for (int index = 0; index < twelveDigits.length(); index++) {
            int digit = twelveDigits.charAt(index) - '0';
            sum += index % 2 == 0 ? digit : digit * 3;
        }
        return (10 - sum % 10) % 10;
    }
}
