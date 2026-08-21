package com.peraerp.sales.verifactu.qr;

import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.hash.VerifactuFieldFormat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Contenido del código QR de cotejo que se imprime en la factura.
 *
 * <p>Es una URL al servicio de la AEAT con cuatro parámetros en un orden fijo: {@code nif},
 * {@code numserie}, {@code fecha} y {@code importe}. El receptor de la factura la escanea y
 * comprueba contra Hacienda que la factura consta.</p>
 *
 * <p>La fecha y el importe se formatean con {@link VerifactuFieldFormat}, el mismo formateador que
 * usa la huella. No es casualidad: el QR y el registro tienen que hablar de la misma factura con
 * los mismos valores, o el cotejo no encuentra nada.</p>
 */
public final class VerifactuQrPayload {

    private VerifactuQrPayload() {
    }

    public static String of(VerifactuEnvironment environment, String issuerTaxId, String invoiceNumber,
                            LocalDate invoiceDate, BigDecimal totalAmount) {
        return environment.qrValidationUrl()
                + "?nif=" + encode(issuerTaxId)
                + "&numserie=" + encode(invoiceNumber)
                + "&fecha=" + encode(VerifactuFieldFormat.date(invoiceDate))
                + "&importe=" + encode(VerifactuFieldFormat.amount(totalAmount));
    }

    /**
     * Codifica un valor para la cadena de consulta.
     *
     * <p>No se usa {@code URLEncoder}: ese aplica codificación de formulario, que convierte el
     * espacio en {@code +} y la barra en {@code %2F}. La barra es legal en una consulta y aparece
     * sin codificar en el ejemplo de la AEAT ({@code numserie=12345678/G33}), así que se respeta.
     * Todo lo demás fuera del conjunto no reservado se codifica en porcentaje.</p>
     */
    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder encoded = new StringBuilder(value.length());
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int character = raw & 0xFF;
            if (isUnreserved(character) || character == '/') {
                encoded.append((char) character);
            } else {
                encoded.append('%').append(String.format("%02X", character));
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '-' || character == '.' || character == '_' || character == '~';
    }
}
