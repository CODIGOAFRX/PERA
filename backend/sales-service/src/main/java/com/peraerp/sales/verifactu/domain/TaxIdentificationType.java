package com.peraerp.sales.verifactu.domain;

/**
 * Tipo de identificación fiscal de un tercero.
 *
 * <p>Se corresponde con el bloque {@code IDDestinatario} de Veri*Factu: un residente en España se
 * identifica por {@link #NIF}; cualquier otro caso viaja como {@code IDOtro} con el código
 * numérico de {@link #code()} y el país de expedición.</p>
 *
 * <p>El enum se declara aquí y también en {@code master-data-service} de forma deliberada. Los servicios
 * de PERA no comparten entidades ni tipos de dominio: lo que viaja entre ellos es el nombre de la
 * constante. Duplicar diez líneas es preferible a acoplar dos bases de datos.</p>
 */
public enum TaxIdentificationType {

    /** NIF español. No lleva código numérico: viaja en el campo NIF, no en IDOtro. */
    NIF(null),
    /** NIF-IVA intracomunitario. */
    VAT_NUMBER("02"),
    /** Pasaporte. */
    PASSPORT("03"),
    /** Documento oficial de identificación expedido por el país de residencia. */
    FOREIGN_OFFICIAL_ID("04"),
    /** Certificado de residencia fiscal. */
    RESIDENCE_CERTIFICATE("05"),
    /** Otro documento probatorio. */
    OTHER_DOCUMENT("06"),
    /** No censado: el destinatario no dispone de identificación fiscal. */
    NOT_REGISTERED("07");

    private final String code;

    TaxIdentificationType(String code) {
        this.code = code;
    }

    /** Código {@code IDType} de la AEAT, o {@code null} cuando se identifica por NIF español. */
    public String code() {
        return code;
    }

    /** {@code true} si debe emitirse dentro de {@code IDOtro} en lugar del campo {@code NIF}. */
    public boolean isForeign() {
        return code != null;
    }
}
