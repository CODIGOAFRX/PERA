package com.peraerp.sales.verifactu.hash;

import com.peraerp.sales.verifactu.domain.InvoiceKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Construye la cadena de entrada del cálculo de la huella de un registro de facturación.
 *
 * <p>Formato: {@code campo1=valor1&campo2=valor2&...&campoN=valorN}, en el orden fijado por la
 * AEAT, codificada en UTF-8. Un campo ausente se emite igualmente con su nombre y valor vacío
 * ({@code Huella=}), porque su presencia forma parte de la cadena.</p>
 *
 * <p>El orden de los campos <strong>no</strong> es alfabético ni arbitrario: es el publicado por
 * la AEAT. No lo reordenes.</p>
 */
public final class FingerprintInput {

    private final Map<String, String> fields;

    private FingerprintInput(Map<String, String> fields) {
        this.fields = fields;
    }

    /**
     * Cadena del registro de alta.
     *
     * @param issuerTaxId    NIF del obligado a expedir la factura
     * @param invoiceNumber  número de serie y factura, tal y como se imprime en el documento
     * @param issueDate      fecha de expedición
     * @param invoiceKind    tipo de factura (F1..R5)
     * @param totalTaxAmount cuota total
     * @param totalAmount    importe total
     * @param previousFingerprint huella del registro anterior de la cadena; {@code null} en el primero
     * @param generatedAt    fecha y hora de generación del registro, con huso
     */
    public static FingerprintInput forAlta(String issuerTaxId,
                                           String invoiceNumber,
                                           LocalDate issueDate,
                                           InvoiceKind invoiceKind,
                                           BigDecimal totalTaxAmount,
                                           BigDecimal totalAmount,
                                           String previousFingerprint,
                                           ZonedDateTime generatedAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("IDEmisorFactura", normalize(issuerTaxId));
        fields.put("NumSerieFactura", normalize(invoiceNumber));
        fields.put("FechaExpedicionFactura", VerifactuFieldFormat.date(issueDate));
        fields.put("TipoFactura", invoiceKind == null ? "" : invoiceKind.code());
        fields.put("CuotaTotal", VerifactuFieldFormat.amount(totalTaxAmount));
        fields.put("ImporteTotal", VerifactuFieldFormat.amount(totalAmount));
        fields.put("Huella", normalize(previousFingerprint));
        fields.put("FechaHoraHusoGenRegistro", VerifactuFieldFormat.timestamp(generatedAt));
        return new FingerprintInput(fields);
    }

    /**
     * Cadena del registro de anulación.
     *
     * @param previousFingerprint huella del registro anterior de la cadena; {@code null} en el primero
     */
    public static FingerprintInput forAnulacion(String issuerTaxId,
                                                String invoiceNumber,
                                                LocalDate issueDate,
                                                String previousFingerprint,
                                                ZonedDateTime generatedAt) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("IDEmisorFacturaAnulada", normalize(issuerTaxId));
        fields.put("NumSerieFacturaAnulada", normalize(invoiceNumber));
        fields.put("FechaExpedicionFacturaAnulada", VerifactuFieldFormat.date(issueDate));
        fields.put("Huella", normalize(previousFingerprint));
        fields.put("FechaHoraHusoGenRegistro", VerifactuFieldFormat.timestamp(generatedAt));
        return new FingerprintInput(fields);
    }

    /** Cadena literal que se pasa a SHA-256. Útil en pruebas y en el diagnóstico de rechazos. */
    public String asString() {
        return fields.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    /** Vista inmutable de los campos, en orden, para volcarla en un log de diagnóstico. */
    public Map<String, String> fields() {
        return Map.copyOf(fields);
    }

    @Override
    public String toString() {
        return asString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
