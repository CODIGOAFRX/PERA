package com.peraerp.sales.verifactu.domain;

/**
 * Modalidad de funcionamiento del sistema informático de facturación.
 */
public enum VerifactuMode {

    /**
     * Los registros se remiten a la AEAT según se generan. Es la modalidad elegida por PERA: la
     * remisión inmediata sustituye a la firma electrónica de cada registro y al registro de
     * eventos obligatorio.
     */
    VERIFACTU,

    /**
     * Los registros se conservan sin remitir. Exige además firma electrónica de cada registro,
     * registro de eventos completo y conservación durante el periodo de prescripción.
     *
     * <p>Modelada pero <strong>no implementada</strong>. Existe para no cerrarse la puerta.</p>
     */
    NO_VERIFACTU
}
