package com.peraerp.sales.verifactu.hash;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Formato canónico de los valores de un registro de facturación Veri*Factu.
 *
 * <p><strong>Regla central:</strong> el texto que entra en el cálculo de la huella debe ser
 * byte a byte el mismo que viaja en el XML remitido a la AEAT. Por eso existe esta clase: para
 * que la serialización XML y el cálculo de la huella no puedan divergir nunca. Cualquier
 * formateo de fecha o importe de un registro debe pasar por aquí.</p>
 */
public final class VerifactuFieldFormat {

    /** {@code FechaExpedicionFactura} y equivalentes: dd-MM-yyyy. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private VerifactuFieldFormat() {
    }

    /** Fecha de expedición en el formato dd-MM-yyyy exigido por la AEAT. */
    public static String date(LocalDate value) {
        return value == null ? "" : DATE.format(value);
    }

    /**
     * Importe con dos decimales y punto como separador, sin separador de millares.
     *
     * <p>Se fija a dos decimales de forma deliberada. La alternativa —emitir el número «tal
     * cual»— haría que {@code 241.4} y {@code 241.40} produjeran huellas distintas para el mismo
     * importe. Los importes internos de PERA tienen escala 4; el redondeo a 2 es HALF_UP.</p>
     */
    public static String amount(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * {@code FechaHoraHusoGenRegistro}: ISO 8601 con huso y <em>sin fracciones de segundo</em>.
     *
     * <p>Ejemplo: {@code 2024-01-01T19:20:30+01:00}. Los milisegundos son motivo de rechazo por
     * validación, así que se truncan explícitamente.</p>
     */
    public static String timestamp(ZonedDateTime value) {
        if (value == null) {
            return "";
        }
        OffsetDateTime truncated = value.truncatedTo(ChronoUnit.SECONDS).toOffsetDateTime();
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(truncated);
    }

    /** Variante por comodidad: instante absoluto expresado en la zona horaria de la empresa. */
    public static String timestamp(java.time.Instant instant, ZoneId zone) {
        return instant == null || zone == null ? "" : timestamp(instant.atZone(zone));
    }
}
