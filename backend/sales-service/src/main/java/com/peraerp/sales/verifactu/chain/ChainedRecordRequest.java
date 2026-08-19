package com.peraerp.sales.verifactu.chain;

import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.RectificationType;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Datos mínimos para encadenar un registro de facturación.
 *
 * <p>Deliberadamente no recibe un {@code CommercialDocument}: el encadenado no debe saber cómo se
 * construye una factura. Quien traduzca el documento a estos campos es el mapeo (fase 3), y así el
 * encadenado se puede probar sin montar media aplicación.</p>
 *
 * @param documentId     factura a la que pertenece el registro
 * @param recordType     alta o anulación
 * @param issuerTaxId    NIF del obligado a expedir
 * @param invoiceNumber  número de la factura tal y como se imprime
 * @param invoiceDate    fecha de expedición
 * @param invoiceKind    F1..R5; obligatorio en las altas, nulo en las anulaciones
 * @param rectificationType criterio de rectificación, solo en rectificativas
 * @param totalTaxAmount cuota total, en euros
 * @param totalAmount    importe total, en euros
 * @param generatedAt    fecha y hora de generación del registro, con el huso de la empresa
 * @param payloadXml     registro serializado; puede ser nulo mientras no exista el serializador
 */
public record ChainedRecordRequest(
        UUID documentId,
        VerifactuRecordType recordType,
        String issuerTaxId,
        String invoiceNumber,
        LocalDate invoiceDate,
        InvoiceKind invoiceKind,
        RectificationType rectificationType,
        BigDecimal totalTaxAmount,
        BigDecimal totalAmount,
        ZonedDateTime generatedAt,
        String payloadXml) {
}
