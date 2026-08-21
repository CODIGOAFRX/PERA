package com.peraerp.sales.verifactu.domain;

/**
 * Valor del campo {@code TipoRectificativa} del registro de facturación.
 *
 * <p>El literal remitido a la AEAT es {@link #code()}; el nombre de la constante es el que se
 * persiste en PERA, porque una sola letra en una columna de base de datos no se entiende al
 * leerla.</p>
 */
public enum RectificationType {

    /**
     * Por sustitución: la rectificativa recoge los importes correctos completos y deja sin efecto
     * la factura rectificada.
     */
    SUBSTITUTION("S"),

    /**
     * Por diferencias: la rectificativa recoge solo la variación respecto a la factura original.
     */
    DIFFERENCES("I");

    private final String code;

    RectificationType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
