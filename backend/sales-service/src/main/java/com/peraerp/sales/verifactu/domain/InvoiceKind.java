package com.peraerp.sales.verifactu.domain;

/**
 * Valor del campo {@code TipoFactura} del registro de facturación (Veri*Factu).
 *
 * <p>El literal enviado a la AEAT es {@link #code()}, no el nombre de la constante.</p>
 */
public enum InvoiceKind {

    /** Factura completa (ordinaria). */
    F1("F1", false),
    /** Factura simplificada y facturas sin identificación del destinatario. */
    F2("F2", false),
    /** Factura emitida en sustitución de facturas simplificadas ya expedidas. */
    F3("F3", false),
    /** Rectificativa por error fundado en derecho y art. 80.1, 80.2 y 80.6 LIVA. */
    R1("R1", true),
    /** Rectificativa por concurso de acreedores (art. 80.3 LIVA). */
    R2("R2", true),
    /** Rectificativa por créditos incobrables (art. 80.4 LIVA). */
    R3("R3", true),
    /** Rectificativa por el resto de causas. */
    R4("R4", true),
    /** Rectificativa de facturas simplificadas. */
    R5("R5", true);

    private final String code;
    private final boolean rectifying;

    InvoiceKind(String code, boolean rectifying) {
        this.code = code;
        this.rectifying = rectifying;
    }

    public String code() {
        return code;
    }

    /** {@code true} para R1..R5, que exigen {@code TipoRectificativa} y facturas rectificadas. */
    public boolean isRectifying() {
        return rectifying;
    }
}
