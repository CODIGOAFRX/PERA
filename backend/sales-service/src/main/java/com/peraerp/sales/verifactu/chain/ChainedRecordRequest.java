package com.peraerp.sales.verifactu.chain;

import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.RectificationType;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Datos mínimos para encadenar un registro de facturación.
 *
 * <p>Deliberadamente no recibe un {@code CommercialDocument}: el encadenado no debe saber cómo se
 * construye una factura. Quien traduzca el documento a estos campos es el mapeo, y así el
 * encadenado se puede probar sin montar media aplicación.</p>
 *
 * <p>Tampoco recibe la fecha de generación, solo la zona horaria. La marca de tiempo la pone la
 * cadena una vez tiene el bloqueo: si la pusiera quien llama, dos emisiones simultáneas podrían
 * sellarse en un orden y encadenarse en el contrario, y la cadena quedaría con el tiempo hacia
 * atrás.</p>
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
 * @param zone           zona horaria de la empresa, que fija el huso de la marca de generación
 * @param payloadFactory construye el XML del registro una vez la cadena conoce la huella y el
 *                       registro anterior; puede ser nulo si no se quiere serializar
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
        ZoneId zone,
        RecordPayloadFactory payloadFactory) {
}
