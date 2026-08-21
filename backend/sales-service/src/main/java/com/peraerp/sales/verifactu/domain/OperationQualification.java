package com.peraerp.sales.verifactu.domain;

/**
 * Valor del campo {@code CalificacionOperacion} de Veri*Factu.
 *
 * <p>Responde a una pregunta que el porcentaje de IVA por sí solo no contesta: si una línea al 0 %
 * lo está por estar exenta, por no estar sujeta o por inversión del sujeto pasivo. Son tres
 * situaciones fiscales distintas que se declaran de forma distinta.</p>
 *
 * <p>Cuando la operación es exenta no se envía este campo, sino {@code OperacionExenta} con la
 * causa concreta. Por eso {@link #EXEMPT} no tiene código: marca que hay que mirar
 * {@link ExemptionCause}.</p>
 *
 * <p>El enum se declara aquí y también en {@code master-data-service} de forma deliberada. Los
 * servicios de PERA no comparten tipos de dominio: lo que viaja entre ellos es el nombre de la
 * constante.</p>
 */
public enum OperationQualification {

    /** S1: operación sujeta y no exenta, sin inversión del sujeto pasivo. Es el caso normal. */
    SUBJECT_NOT_EXEMPT("S1"),

    /** S2: operación sujeta y no exenta con inversión del sujeto pasivo. */
    REVERSE_CHARGE("S2"),

    /** N1: operación no sujeta por los artículos 7, 14 y otros de la LIVA. */
    NOT_SUBJECT("N1"),

    /** N2: operación no sujeta por reglas de localización. */
    NOT_SUBJECT_LOCATION("N2"),

    /** Operación exenta: la causa concreta viaja en {@link ExemptionCause}. */
    EXEMPT(null);

    private final String code;

    OperationQualification(String code) {
        this.code = code;
    }

    /** Literal remitido a la AEAT, o {@code null} en las exentas. */
    public String code() {
        return code;
    }

    public boolean isExempt() {
        return this == EXEMPT;
    }
}
