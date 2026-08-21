package com.peraerp.sales.verifactu.chain;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.verifactu.domain.InvoiceChainHead;
import com.peraerp.sales.verifactu.domain.InvoiceChainHeadRepository;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.hash.FingerprintInput;
import com.peraerp.sales.verifactu.hash.RecordFingerprint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Encadena registros de facturación.
 *
 * <p>Es el único punto del sistema autorizado a crear un {@link VerifactuRecord}, y lo hace bajo
 * el bloqueo del puntero de cadena de la empresa. La huella de cada registro incorpora la del
 * anterior: si dos facturas simultáneas leyeran el mismo valor, la cadena quedaría bifurcada y
 * cualquier verificación posterior de la AEAT la detectaría.</p>
 *
 * <p>El método exige transacción existente ({@link Propagation#MANDATORY}) a propósito. Encadenar
 * un registro y expedir la factura tienen que ser una sola operación atómica: una factura sin
 * registro, o un hueco en la cadena, son fallos peores que no emitir.</p>
 */
@Service
public class VerifactuChainService {

    private final InvoiceChainHeadRepository chainHeads;
    private final VerifactuRecordRepository records;

    public VerifactuChainService(InvoiceChainHeadRepository chainHeads, VerifactuRecordRepository records) {
        this.chainHeads = chainHeads;
        this.records = records;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public VerifactuRecord append(UUID companyId, ChainedRecordRequest request) {
        validate(companyId, request);

        InvoiceChainHead head = lockChainHead(companyId);
        String previousFingerprint = head.getLastFingerprint();

        String fingerprint = RecordFingerprint.of(fingerprintInput(request, previousFingerprint));

        VerifactuRecord record = records.save(new VerifactuRecord(companyId, request.documentId(),
                request.recordType(), head.getNextSequence(), request.issuerTaxId(), request.invoiceNumber(),
                request.invoiceDate(), request.invoiceKind(), request.rectificationType(),
                request.totalTaxAmount(), request.totalAmount(), previousFingerprint, fingerprint,
                request.generatedAt(), request.payloadXml()));

        head.advance(record.getId(), fingerprint, request.generatedAt());
        chainHeads.save(head);
        return record;
    }

    private FingerprintInput fingerprintInput(ChainedRecordRequest request, String previousFingerprint) {
        if (request.recordType() == VerifactuRecordType.ANULACION) {
            return FingerprintInput.forAnulacion(request.issuerTaxId(), request.invoiceNumber(),
                    request.invoiceDate(), previousFingerprint, request.generatedAt());
        }
        return FingerprintInput.forAlta(request.issuerTaxId(), request.invoiceNumber(), request.invoiceDate(),
                request.invoiceKind(), request.totalTaxAmount(), request.totalAmount(), previousFingerprint,
                request.generatedAt());
    }

    /**
     * Obtiene el puntero de cadena bloqueado.
     *
     * <p>El {@code ensureHead} previo cubre el único momento en el que no hay fila que bloquear: la
     * primera factura de la empresa. Insertar con {@code ON CONFLICT DO NOTHING} y volver a leer es
     * más barato y más seguro que comprobar y luego insertar.</p>
     */
    private InvoiceChainHead lockChainHead(UUID companyId) {
        chainHeads.ensureHead(UUID.randomUUID(), companyId, Instant.now());
        return chainHeads.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo inicializar la cadena de registros de la empresa " + companyId + "."));
    }

    private void validate(UUID companyId, ChainedRecordRequest request) {
        if (companyId == null) {
            throw new IllegalArgumentException("El encadenado exige una empresa.");
        }
        if (request.documentId() == null || request.recordType() == null || request.generatedAt() == null) {
            throw new IllegalArgumentException("El registro exige documento, tipo y fecha de generación.");
        }
        if (isBlank(request.issuerTaxId())) {
            throw new BusinessRuleException(
                    "La empresa no tiene NIF configurado y no puede expedir registros de facturación.");
        }
        if (isBlank(request.invoiceNumber()) || request.invoiceDate() == null) {
            throw new BusinessRuleException("El registro exige número y fecha de la factura.");
        }
        if (request.recordType() == VerifactuRecordType.ALTA) {
            if (request.invoiceKind() == null) {
                throw new BusinessRuleException("Un registro de alta exige el tipo de factura (F1 a R5).");
            }
            if (request.totalTaxAmount() == null || request.totalAmount() == null) {
                throw new BusinessRuleException("Un registro de alta exige cuota e importe totales.");
            }
        }
        // El registro no puede generarse antes de que la factura exista. Es el error que produce
        // registros fechados días antes de su propia factura y que la AEAT acaba rechazando.
        if (request.generatedAt().toLocalDate().isBefore(request.invoiceDate())) {
            throw new BusinessRuleException(
                    "La fecha de generación del registro (" + request.generatedAt().toLocalDate()
                            + ") es anterior a la fecha de expedición de la factura ("
                            + request.invoiceDate() + ").");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
