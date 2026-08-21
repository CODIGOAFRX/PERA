package com.peraerp.masterdata.party;

import java.util.Locale;

/**
 * Comprobación del dígito de control de un identificador fiscal español.
 *
 * <p>Existe porque la AEAT rechaza un registro con un NIF mal formado, y ese rechazo llega días
 * después de emitir la factura. Comprobarlo al dar de alta el tercero convierte un problema
 * fiscal en un mensaje de formulario.</p>
 *
 * <p><strong>Comprueba el dígito de control, no que el identificador exista.</strong> Un NIF puede
 * cuadrar y no estar asignado a nadie: {@code B00000000} supera esta validación. Lo que evita es la
 * errata —un dígito cambiado, una letra mal copiada—, que es la causa habitual de rechazo.</p>
 *
 * <p>Cubre las tres formas del identificador español: DNI (ocho dígitos y letra), NIE (X, Y o Z
 * más siete dígitos y letra) y CIF de persona jurídica (letra, siete dígitos y control, que según
 * la forma societaria es dígito o letra). No valida identificadores extranjeros: para esos la
 * ficha del tercero indica el país y el tipo de documento, y no hay un algoritmo común.</p>
 */
public final class SpanishTaxIdValidator {

    private static final String CONTROL_LETTERS = "TRWAGMYFPDXBNJZSQVHLCKE";
    private static final String ORGANISATION_CONTROL_LETTERS = "JABCDEFGHI";
    /** Formas societarias cuyo dígito de control se expresa siempre como letra. */
    private static final String LETTER_ONLY_ORGANISATIONS = "KPQSNW";
    /** Formas societarias cuyo dígito de control es siempre numérico. */
    private static final String DIGIT_ONLY_ORGANISATIONS = "ABEH";

    private SpanishTaxIdValidator() {
    }

    public static boolean isValid(String taxId) {
        if (taxId == null) {
            return false;
        }
        String normalized = taxId.trim().toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "");
        if (normalized.length() != 9) {
            return false;
        }
        char first = normalized.charAt(0);
        if (Character.isDigit(first)) {
            return isValidNaturalPerson(normalized, normalized.substring(0, 8));
        }
        if (first == 'X' || first == 'Y' || first == 'Z') {
            String digits = ("" + "XYZ".indexOf(first)) + normalized.substring(1, 8);
            return isValidNaturalPerson(normalized, digits);
        }
        return isValidOrganisation(normalized);
    }

    /** DNI y NIE comparten algoritmo: el resto de dividir el número entre 23 elige la letra. */
    private static boolean isValidNaturalPerson(String taxId, String digits) {
        if (!digits.matches("\\d{8}")) {
            return false;
        }
        char expected = CONTROL_LETTERS.charAt(Integer.parseInt(digits) % 23);
        return taxId.charAt(8) == expected;
    }

    private static boolean isValidOrganisation(String taxId) {
        char organisation = taxId.charAt(0);
        if ("ABCDEFGHJKLMNPQRSUVW".indexOf(organisation) < 0) {
            return false;
        }
        String body = taxId.substring(1, 8);
        if (!body.matches("\\d{7}")) {
            return false;
        }
        int sum = 0;
        for (int position = 0; position < body.length(); position++) {
            int digit = body.charAt(position) - '0';
            if (position % 2 == 0) {
                // Posiciones impares del número (1.ª, 3.ª…): se duplican y se suman sus cifras.
                int doubled = digit * 2;
                sum += doubled / 10 + doubled % 10;
            } else {
                sum += digit;
            }
        }
        int control = (10 - (sum % 10)) % 10;
        char actual = taxId.charAt(8);
        if (LETTER_ONLY_ORGANISATIONS.indexOf(organisation) >= 0) {
            return actual == ORGANISATION_CONTROL_LETTERS.charAt(control);
        }
        if (DIGIT_ONLY_ORGANISATIONS.indexOf(organisation) >= 0) {
            return actual == (char) ('0' + control);
        }
        // El resto admite ambas formas.
        return actual == (char) ('0' + control) || actual == ORGANISATION_CONTROL_LETTERS.charAt(control);
    }
}
