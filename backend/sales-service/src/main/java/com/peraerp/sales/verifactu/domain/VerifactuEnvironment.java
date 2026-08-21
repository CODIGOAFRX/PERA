package com.peraerp.sales.verifactu.domain;

/**
 * Entorno de la AEAT contra el que opera una empresa.
 *
 * <p>Que esto sea un campo de configuración y no una propiedad global es intencionado: en una
 * instalación multiempresa puede haber una empresa ya en producción y otra todavía probando.</p>
 */
public enum VerifactuEnvironment {

    /** Preproducción. Los registros no tienen efecto fiscal alguno. */
    TEST("https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR"),

    /** Producción. */
    PRODUCTION("https://www2.agenciatributaria.es/wlpl/TIKE-CONT/ValidarQR");

    private final String qrValidationUrl;

    VerifactuEnvironment(String qrValidationUrl) {
        this.qrValidationUrl = qrValidationUrl;
    }

    /** URL base del servicio de cotejo que se imprime en el QR de la factura. */
    public String qrValidationUrl() {
        return qrValidationUrl;
    }
}
