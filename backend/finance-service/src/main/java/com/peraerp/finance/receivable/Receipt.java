package com.peraerp.finance.receivable;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Recibo de cobro de un vencimiento de factura. Se cobra entero: no admite cobros parciales.
 */
@Entity
@Table(name = "receipts", uniqueConstraints = @UniqueConstraint(
        name = "uk_receipt_number", columnNames = {"company_id", "receipt_number"}))
public class Receipt extends CompanyScopedEntity {

    @Column(name = "receipt_number", nullable = false, length = 50, updatable = false)
    private String receiptNumber;
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;
    @Column(name = "customer_code_snapshot", length = 60)
    private String customerCodeSnapshot;
    @Column(name = "customer_name_snapshot", nullable = false, length = 180)
    private String customerNameSnapshot;
    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Column(name = "document_number_snapshot", nullable = false, length = 80)
    private String documentNumberSnapshot;
    @Column(name = "due_date_id", updatable = false)
    private UUID dueDateId;
    @Column(name = "installment_number", nullable = false, updatable = false)
    private int installmentNumber;
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;
    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private String currencyCode;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(name = "collection_date")
    private LocalDate collectionDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "collection_method", length = 30)
    private CollectionMethod collectionMethod;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReceiptStatus status = ReceiptStatus.PENDING;
    @Column(name = "return_date")
    private LocalDate returnDate;
    @Column(name = "return_reason", length = 300)
    private String returnReason;
    @Column(name = "remittance_id")
    private UUID remittanceId;
    @Column(name = "bank_account", length = 80)
    private String bankAccount;
    @Column(columnDefinition = "text")
    private String notes;

    protected Receipt() {
    }

    public Receipt(UUID companyId, String receiptNumber, DocumentDueDate dueDate, UUID customerId,
                   String customerCodeSnapshot, String customerNameSnapshot, String documentNumberSnapshot,
                   String currencyCode) {
        super(companyId);
        this.receiptNumber = receiptNumber;
        this.customerId = customerId;
        this.customerCodeSnapshot = customerCodeSnapshot;
        this.customerNameSnapshot = customerNameSnapshot;
        this.documentId = dueDate.getDocumentId();
        this.documentNumberSnapshot = documentNumberSnapshot;
        this.dueDateId = dueDate.getId();
        this.installmentNumber = dueDate.getInstallmentNumber();
        this.amount = dueDate.getAmount();
        this.currencyCode = currencyCode;
        this.dueDate = dueDate.getDueDate();
    }

    /** Cobro directo en ventanilla, por transferencia, tarjeta... */
    public void collect(LocalDate collectionDate, CollectionMethod method, String notes) {
        if (status != ReceiptStatus.PENDING) {
            throw new IllegalStateException("Solo se puede cobrar un recibo pendiente.");
        }
        if (remittanceId != null) {
            throw new IllegalStateException("El recibo está en una remesa. Sácalo de la remesa antes de cobrarlo.");
        }
        markCollected(collectionDate, method);
        if (notes != null) {
            this.notes = notes;
        }
    }

    /** Lo incluye en una remesa en borrador. Sigue pendiente hasta que la remesa se envía al banco. */
    public void assignTo(UUID remittanceId) {
        if (status != ReceiptStatus.PENDING || this.remittanceId != null) {
            throw new IllegalStateException("El recibo " + receiptNumber + " no está disponible para remesar.");
        }
        this.remittanceId = remittanceId;
    }

    public void markRemitted() {
        if (status != ReceiptStatus.PENDING || remittanceId == null) {
            throw new IllegalStateException("El recibo " + receiptNumber + " no se puede enviar al banco.");
        }
        this.status = ReceiptStatus.REMITTED;
    }

    /** El banco abona la remesa. */
    public void settle(LocalDate settlementDate) {
        if (status != ReceiptStatus.REMITTED) {
            throw new IllegalStateException("El recibo " + receiptNumber + " no está presentado al banco.");
        }
        markCollected(settlementDate, CollectionMethod.DIRECT_DEBIT);
    }

    /** Sale de su remesa y vuelve a estar pendiente de cobro. */
    public void releaseFromRemittance() {
        if (status != ReceiptStatus.PENDING && status != ReceiptStatus.REMITTED) {
            throw new IllegalStateException("El recibo " + receiptNumber + " ya no se puede sacar de la remesa.");
        }
        this.status = ReceiptStatus.PENDING;
        this.remittanceId = null;
    }

    public void markReturned(LocalDate returnDate, String reason) {
        if (status != ReceiptStatus.COLLECTED && status != ReceiptStatus.REMITTED) {
            throw new IllegalStateException("Solo se puede devolver un recibo cobrado o presentado al banco.");
        }
        if (collectionDate != null && returnDate.isBefore(collectionDate)) {
            throw new IllegalStateException("La devolución no puede ser anterior al cobro.");
        }
        this.status = ReceiptStatus.RETURNED;
        this.returnDate = returnDate;
        this.returnReason = reason;
    }

    /** Vuelve a poner en circulación un recibo devuelto, con un nuevo vencimiento si se indica. */
    public void reopen(LocalDate newDueDate) {
        if (status != ReceiptStatus.RETURNED) {
            throw new IllegalStateException("Solo se puede reabrir un recibo devuelto.");
        }
        this.status = ReceiptStatus.PENDING;
        this.remittanceId = null;
        this.collectionDate = null;
        this.collectionMethod = null;
        if (newDueDate != null) {
            this.dueDate = newDueDate;
        }
    }

    public void cancel() {
        if (status != ReceiptStatus.PENDING && status != ReceiptStatus.RETURNED) {
            throw new IllegalStateException("Solo se puede anular un recibo pendiente o devuelto.");
        }
        if (status == ReceiptStatus.PENDING && remittanceId != null) {
            throw new IllegalStateException("El recibo está en una remesa. Sácalo de la remesa antes de anularlo.");
        }
        this.status = ReceiptStatus.CANCELLED;
    }

    private void markCollected(LocalDate date, CollectionMethod method) {
        this.status = ReceiptStatus.COLLECTED;
        this.collectionDate = date;
        this.collectionMethod = method;
        this.returnDate = null;
        this.returnReason = null;
    }

    public String getReceiptNumber() { return receiptNumber; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerCodeSnapshot() { return customerCodeSnapshot; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot; }
    public UUID getDocumentId() { return documentId; }
    public String getDocumentNumberSnapshot() { return documentNumberSnapshot; }
    public UUID getDueDateId() { return dueDateId; }
    public int getInstallmentNumber() { return installmentNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getCollectionDate() { return collectionDate; }
    public CollectionMethod getCollectionMethod() { return collectionMethod; }
    public ReceiptStatus getStatus() { return status; }
    public LocalDate getReturnDate() { return returnDate; }
    public String getReturnReason() { return returnReason; }
    public UUID getRemittanceId() { return remittanceId; }
    public String getNotes() { return notes; }
}
