package com.peraerp.sales.document;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "commercial_documents", uniqueConstraints = @UniqueConstraint(name = "uk_document_number", columnNames = {"company_id", "document_type", "document_number"}))
public class CommercialDocument extends CompanyScopedEntity {
    @Column(name = "document_number", nullable = false, length = 40)
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
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_APPLICABLE;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;
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
        this.sourceDocumentId=sourceDocumentId; this.paymentMethodId=paymentMethodId; this.notes=notes;
        this.paymentStatus = type == DocumentType.INVOICE ? PaymentStatus.PENDING : PaymentStatus.NOT_APPLICABLE;
    }

    public void addLine(DocumentLine line) { line.attachTo(this, lines.size() + 1); lines.add(line); }
    public void recalculate(DocumentAmountsCalculator calculator) {
        lines.forEach(line -> line.recalculate(calculator));
        netAmount = lines.stream().map(DocumentLine::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        taxAmount = lines.stream().map(DocumentLine::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        totalAmount = netAmount.add(taxAmount);
    }
    public void confirm() { status = DocumentStatus.CONFIRMED; }
    public void markConverted() { status = DocumentStatus.CONVERTED; }
    public void updatePaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getDocumentNumber() { return documentNumber; }
    public DocumentType getType() { return type; }
    public DocumentStatus getStatus() { return status; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerCodeSnapshot() { return customerCodeSnapshot; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public String getCurrency() { return currency; }
    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public UUID getPaymentMethodId() { return paymentMethodId; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getNotes() { return notes; }
    public List<DocumentLine> getLines() { return List.copyOf(lines); }
}
