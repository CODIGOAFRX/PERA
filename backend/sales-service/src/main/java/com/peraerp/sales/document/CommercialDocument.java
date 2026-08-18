package com.peraerp.sales.document;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "commercial_documents", uniqueConstraints = @UniqueConstraint(name = "uk_document_number", columnNames = {"company_id", "document_type", "document_number"}))
public class CommercialDocument extends CompanyScopedEntity {
    @Column(name = "document_number", nullable = false, length = 80)
    private String documentNumber;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private DocumentStatus status = DocumentStatus.DRAFT;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "customer_code_snapshot", nullable = false, length = 60)
    private String customerCodeSnapshot;
    @Column(name = "customer_name_snapshot", nullable = false, length = 180)
    private String customerNameSnapshot;
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(nullable = false, length = 3)
    private String currency = "EUR";
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency = "EUR";
    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 10)
    private BigDecimal exchangeRate = BigDecimal.ONE;
    @Column(name = "exchange_rate_date", nullable = false)
    private LocalDate exchangeRateDate;
    @Column(name = "exchange_rate_source", nullable = false, length = 120)
    private String exchangeRateSource = "IDENTITY";
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;
    @Enumerated(EnumType.STRING) @Column(name = "quote_status", length = 30)
    private QuoteStatus quoteStatus;
    @Column(name = "quote_valid_until")
    private LocalDate quoteValidUntil;
    @Column(name = "quote_decided_at")
    private Instant quoteDecidedAt;
    @Column(name = "quote_rejection_reason", length = 500)
    private String quoteRejectionReason;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_APPLICABLE;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "base_net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseNetAmount = BigDecimal.ZERO;
    @Column(name = "base_tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseTaxAmount = BigDecimal.ZERO;
    @Column(name = "base_total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseTotalAmount = BigDecimal.ZERO;
    @Column(columnDefinition = "text")
    private String notes;
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<DocumentLine> lines = new ArrayList<>();

    protected CommercialDocument() {}

    public CommercialDocument(UUID companyId, String documentNumber, DocumentType type, UUID customerId,
                              String customerCodeSnapshot, String customerNameSnapshot, LocalDate issueDate,
                              LocalDate dueDate, String currency, UUID sourceDocumentId, UUID paymentMethodId,
                              String notes) {
        super(companyId); this.documentNumber=documentNumber; this.type=type; this.customerId=customerId;
        this.customerCodeSnapshot=customerCodeSnapshot; this.customerNameSnapshot=customerNameSnapshot;
        this.issueDate=issueDate; this.dueDate=dueDate; this.currency=currency == null ? "EUR" : currency;
        this.baseCurrency = this.currency; this.exchangeRateDate = issueDate;
        this.sourceDocumentId=sourceDocumentId; this.paymentMethodId=paymentMethodId; this.notes=notes;
        this.paymentStatus = type == DocumentType.INVOICE ? PaymentStatus.PENDING : PaymentStatus.NOT_APPLICABLE;
        if (type == DocumentType.QUOTE) {
            this.quoteStatus = QuoteStatus.DRAFT;
            this.quoteValidUntil = issueDate.plusDays(30);
        }
    }

    public void addLine(DocumentLine line) { line.attachTo(this, lines.size() + 1); lines.add(line); }
    public void recalculate(DocumentAmountsCalculator calculator) {
        lines.forEach(line -> line.recalculate(calculator));
        netAmount = lines.stream().map(DocumentLine::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        taxAmount = lines.stream().map(DocumentLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = netAmount.add(taxAmount);
        recalculateBaseAmounts();
    }
    public void applyCurrencySnapshot(String baseCurrency, BigDecimal exchangeRate, LocalDate rateDate, String source) {
        this.baseCurrency = baseCurrency;
        this.exchangeRate = exchangeRate;
        this.exchangeRateDate = rateDate;
        this.exchangeRateSource = source;
        recalculateBaseAmounts();
    }
    private void recalculateBaseAmounts() {
        baseNetAmount = netAmount.multiply(exchangeRate).setScale(4, java.math.RoundingMode.HALF_UP);
        baseTaxAmount = taxAmount.multiply(exchangeRate).setScale(4, java.math.RoundingMode.HALF_UP);
        baseTotalAmount = totalAmount.multiply(exchangeRate).setScale(4, java.math.RoundingMode.HALF_UP);
    }
    public void confirm() {
        if (status != DocumentStatus.DRAFT) {
            throw new com.peraerp.platform.domain.BusinessRuleException("Solo se pueden confirmar documentos en borrador.");
        }
        status = DocumentStatus.CONFIRMED;
        if (type == DocumentType.QUOTE) quoteStatus = QuoteStatus.SENT;
    }
    public void configureQuoteValidity(LocalDate validUntil) {
        requireQuote();
        if (validUntil == null || validUntil.isBefore(issueDate)) {
            throw new com.peraerp.platform.domain.BusinessRuleException("La validez del presupuesto no puede ser anterior a su fecha de emisión.");
        }
        if (quoteStatus != QuoteStatus.DRAFT) {
            throw new com.peraerp.platform.domain.BusinessRuleException("La validez solo se puede modificar en un presupuesto borrador.");
        }
        quoteValidUntil = validUntil;
    }
    public void acceptQuote(Instant decidedAt, LocalDate today) {
        requireQuote();
        expireQuoteIfDue(today);
        if (quoteStatus != QuoteStatus.SENT) {
            throw new com.peraerp.platform.domain.BusinessRuleException("Solo se pueden aceptar presupuestos enviados y vigentes.");
        }
        quoteStatus = QuoteStatus.ACCEPTED;
        quoteDecidedAt = decidedAt;
        quoteRejectionReason = null;
    }
    public void rejectQuote(String reason, Instant decidedAt, LocalDate today) {
        requireQuote();
        expireQuoteIfDue(today);
        if (quoteStatus != QuoteStatus.SENT) {
            throw new com.peraerp.platform.domain.BusinessRuleException("Solo se pueden rechazar presupuestos enviados y vigentes.");
        }
        quoteStatus = QuoteStatus.REJECTED;
        quoteDecidedAt = decidedAt;
        quoteRejectionReason = reason;
        status = DocumentStatus.CANCELLED;
    }
    public boolean expireQuoteIfDue(LocalDate today) {
        if (type == DocumentType.QUOTE && quoteStatus == QuoteStatus.SENT && quoteValidUntil.isBefore(today)) {
            quoteStatus = QuoteStatus.EXPIRED;
            status = DocumentStatus.CANCELLED;
            quoteDecidedAt = Instant.now();
            return true;
        }
        return false;
    }
    public void markConverted() {
        status = DocumentStatus.CONVERTED;
        if (type == DocumentType.QUOTE) quoteStatus = QuoteStatus.CONVERTED;
    }
    public void updatePaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    private void requireQuote() {
        if (type != DocumentType.QUOTE) {
            throw new com.peraerp.platform.domain.BusinessRuleException("La operación solo está disponible para presupuestos.");
        }
    }

    public String getDocumentNumber() { return documentNumber; }
    public DocumentType getType() { return type; }
    public DocumentStatus getStatus() { return status; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerCodeSnapshot() { return customerCodeSnapshot; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public String getCurrency() { return currency; }
    public String getBaseCurrency() { return baseCurrency; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public LocalDate getExchangeRateDate() { return exchangeRateDate; }
    public String getExchangeRateSource() { return exchangeRateSource; }
    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public UUID getPaymentMethodId() { return paymentMethodId; }
    public QuoteStatus getQuoteStatus() { return quoteStatus; }
    public LocalDate getQuoteValidUntil() { return quoteValidUntil; }
    public Instant getQuoteDecidedAt() { return quoteDecidedAt; }
    public String getQuoteRejectionReason() { return quoteRejectionReason; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getBaseNetAmount() { return baseNetAmount; }
    public BigDecimal getBaseTaxAmount() { return baseTaxAmount; }
    public BigDecimal getBaseTotalAmount() { return baseTotalAmount; }
    public String getNotes() { return notes; }
    public List<DocumentLine> getLines() { return List.copyOf(lines); }
}
