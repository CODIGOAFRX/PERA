package com.peraerp.sales.verifactu.api;

import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.domain.VerifactuState;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registro de facturación tal y como lo ve la aplicación.
 *
 * @param qrPayload contenido exacto del código QR. Lo construye el servidor porque el orden de los
 *                  parámetros y el formato de fecha e importe son especificación, no presentación
 */
public record VerifactuRecordResponse(
        UUID id,
        UUID documentId,
        VerifactuRecordType recordType,
        long sequenceNumber,
        String issuerTaxId,
        String invoiceNumber,
        LocalDate invoiceDate,
        InvoiceKind invoiceKind,
        BigDecimal totalTaxAmount,
        BigDecimal totalAmount,
        String previousFingerprint,
        String fingerprint,
        Instant generatedAt,
        VerifactuState state,
        String aeatCsv,
        String qrPayload) {

    public static VerifactuRecordResponse from(VerifactuRecord record, String qrPayload) {
        return new VerifactuRecordResponse(record.getId(), record.getDocumentId(), record.getRecordType(),
                record.getSequenceNumber(), record.getIssuerTaxId(), record.getInvoiceNumber(),
                record.getInvoiceDate(), record.getInvoiceKind(), record.getTotalTaxAmount(),
                record.getTotalAmount(), record.getPreviousFingerprint(), record.getFingerprint(),
                record.getGeneratedAt(), record.getState(), record.getAeatCsv(), qrPayload);
    }
}
