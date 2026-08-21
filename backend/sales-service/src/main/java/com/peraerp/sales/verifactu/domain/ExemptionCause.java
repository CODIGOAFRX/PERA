package com.peraerp.sales.verifactu.domain;

/**
 * Valor del campo {@code OperacionExenta} de Veri*Factu: por qué está exenta una operación.
 *
 * <p>Un booleano {@code exempt} no basta. La AEAT distingue seis causas y cada una responde a un
 * artículo distinto de la Ley del IVA; declarar la equivocada es declarar mal.</p>
 *
 * <p>El enum se declara aquí y también en {@code master-data-service} de forma deliberada. Los
 * servicios de PERA no comparten tipos de dominio: lo que viaja entre ellos es el nombre de la
 * constante.</p>
 */
public enum ExemptionCause {

    /** E1: exenta por el artículo 20 de la LIVA (exenciones en operaciones interiores). */
    ARTICLE_20("E1"),

    /** E2: exenta por el artículo 21 (exportaciones). */
    ARTICLE_21("E2"),

    /** E3: exenta por el artículo 22 (operaciones asimiladas a las exportaciones). */
    ARTICLE_22("E3"),

    /** E4: exenta por los artículos 23 y 24 (regímenes suspensivos y zonas francas). */
    ARTICLES_23_AND_24("E4"),

    /** E5: exenta por el artículo 25 (entregas intracomunitarias de bienes). */
    ARTICLE_25("E5"),

    /** E6: exenta por otras causas. */
    OTHER("E6");

    private final String code;

    ExemptionCause(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
