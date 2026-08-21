package com.peraerp.sales.verifactu.domain;

/**
 * Situación de un registro respecto de la AEAT.
 *
 * <p>El estado describe la <em>remisión</em>, nunca la validez de la factura. Una factura expedida
 * lo está aunque su registro siga en {@link #PENDING}: si la AEAT no responde, la factura se emite
 * igual y el registro se reintenta.</p>
 */
public enum VerifactuState {

    /** Generado y encadenado, pendiente de remitir. */
    PENDING,

    /** Remitido, sin respuesta definitiva todavía. */
    SENT,

    /** Aceptado por la AEAT. */
    ACCEPTED,

    /**
     * Aceptado con errores: la AEAT lo admite pero señala defectos. Debe subsanarse.
     */
    ACCEPTED_WITH_ERRORS,

    /** Rechazado. El registro no consta y hay que corregir y volver a remitir. */
    REJECTED;

    /** {@code true} si todavía hay que hacer algo con este registro. */
    public boolean requiresAttention() {
        return this == PENDING || this == SENT || this == ACCEPTED_WITH_ERRORS || this == REJECTED;
    }
}
