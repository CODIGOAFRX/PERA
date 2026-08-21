package com.peraerp.sales.verifactu.domain;

/**
 * Tipo de registro de facturación.
 */
public enum VerifactuRecordType {

    /** Registro de alta: la factura se expide. */
    ALTA,

    /**
     * Registro de anulación: la factura se retira por no haber llegado a existir.
     *
     * <p>No confundir con rectificar. Se anula lo que nunca debió expedirse; se rectifica lo que
     * se expidió con datos incorrectos.</p>
     */
    ANULACION
}
