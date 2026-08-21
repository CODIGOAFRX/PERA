package com.peraerp.sales.verifactu.domain;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Registro de facturación remitido —o pendiente de remitir— a la AEAT.
 *
 * <p>Una vez creado, el contenido fiscal del registro <strong>no cambia</strong>: ni la huella, ni
 * los importes, ni el XML serializado. Lo único que evoluciona es su situación frente a la AEAT
 * ({@link VerifactuState}) y los datos de la respuesta. Por eso no hay setters del contenido.</p>
 */
@Entity
@Table(name = "verifactu_records", uniqueConstraints = {
        @UniqueConstraint(name = "uk_verifactu_record_sequence", columnNames = {"company_id", "sequence_number"}),
        @UniqueConstraint(name = "uk_verifactu_record_fingerprint", columnNames = {"company_id", "fingerprint"})})
public class VerifactuRecord extends CompanyScopedEntity {

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Enumerated(EnumType.STRING) @Column(name = "record_type", nullable = false, updatable = false, length = 20)
    private VerifactuRecordType recordType;
    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;
    @Column(name = "issuer_tax_id", nullable = false, updatable = false, length = 20)
    private String issuerTaxId;
    @Column(name = "invoice_number", nullable = false, updatable = false, length = 80)
    private String invoiceNumber;
    @Column(name = "invoice_date", nullable = false, updatable = false)
    private LocalDate invoiceDate;
    @Enumerated(EnumType.STRING) @Column(name = "invoice_kind", updatable = false, length = 2)
    private InvoiceKind invoiceKind;
    @Enumerated(EnumType.STRING) @Column(name = "rectification_type", updatable = false, length = 12)
    private RectificationType rectificationType;
    @Column(name = "total_tax_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal totalTaxAmount;
    @Column(name = "total_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;
    @Column(name = "previous_fingerprint", updatable = false, length = 64)
    private String previousFingerprint;
    @Column(name = "fingerprint", nullable = false, updatable = false, length = 64)
    private String fingerprint;
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;
    @Column(name = "payload_xml", updatable = false, columnDefinition = "text")
    private String payloadXml;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private VerifactuState state = VerifactuState.PENDING;
    @Column(name = "aeat_csv", length = 50)
    private String aeatCsv;
    @Column(name = "aeat_response", columnDefinition = "text")
    private String aeatResponse;
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    protected VerifactuRecord() {}

    public VerifactuRecord(UUID companyId, UUID documentId, VerifactuRecordType recordType, long sequenceNumber,
                           String issuerTaxId, String invoiceNumber, LocalDate invoiceDate,
                           InvoiceKind invoiceKind, RectificationType rectificationType,
                           BigDecimal totalTaxAmount, BigDecimal totalAmount,
                           String previousFingerprint, String fingerprint, ZonedDateTime generatedAt,
                           String payloadXml) {
        super(companyId);
        this.documentId = documentId;
        this.recordType = recordType;
        this.sequenceNumber = sequenceNumber;
        this.issuerTaxId = issuerTaxId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceDate = invoiceDate;
        this.invoiceKind = invoiceKind;
        this.rectificationType = rectificationType;
        this.totalTaxAmount = totalTaxAmount;
        this.totalAmount = totalAmount;
        this.previousFingerprint = previousFingerprint;
        this.fingerprint = fingerprint;
        this.generatedAt = generatedAt.toInstant();
        this.payloadXml = payloadXml;
    }

    /** Marca el registro como remitido y en espera de respuesta. */
    public void markSent(Instant attemptedAt) {
        this.state = VerifactuState.SENT;
        this.lastAttemptAt = attemptedAt;
        this.attemptCount = this.attemptCount + 1;
    }

    /**
     * Aplica la respuesta de la AEAT.
     *
     * <p>Nótese que no se admite volver a {@link VerifactuState#PENDING}: un registro aceptado no
     * se «desacepta». Reintentar tras un rechazo exige generar un registro nuevo, porque la huella
     * del rechazado ya está encadenada.</p>
     */
    public void applyResponse(VerifactuState state, String aeatCsv, String aeatResponse, Instant respondedAt) {
        if (state == VerifactuState.PENDING) {
            throw new BusinessRuleException("Una respuesta de la AEAT no puede dejar el registro como pendiente.");
        }
        this.state = state;
        this.aeatCsv = aeatCsv;
        this.aeatResponse = aeatResponse;
        this.lastAttemptAt = respondedAt;
    }

    public UUID getDocumentId() { return documentId; }
    public VerifactuRecordType getRecordType() { return recordType; }
    public long getSequenceNumber() { return sequenceNumber; }
    public String getIssuerTaxId() { return issuerTaxId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public InvoiceKind getInvoiceKind() { return invoiceKind; }
    public RectificationType getRectificationType() { return rectificationType; }
    public BigDecimal getTotalTaxAmount() { return totalTaxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getPreviousFingerprint() { return previousFingerprint; }
    public String getFingerprint() { return fingerprint; }
    public Instant getGeneratedAt() { return generatedAt; }
    public String getPayloadXml() { return payloadXml; }
    public VerifactuState getState() { return state; }
    public String getAeatCsv() { return aeatCsv; }
    public String getAeatResponse() { return aeatResponse; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public int getAttemptCount() { return attemptCount; }
}
