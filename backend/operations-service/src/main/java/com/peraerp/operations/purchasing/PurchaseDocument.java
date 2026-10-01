package com.peraerp.operations.purchasing;

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

@Entity
@Table(name = "purchase_documents", uniqueConstraints = {
        @UniqueConstraint(name = "uk_purchase_document_number",
                columnNames = {"company_id", "document_type", "document_number"}),
        @UniqueConstraint(name = "uk_purchase_document_company_id", columnNames = {"company_id", "id"})
})
public class PurchaseDocument extends CompanyScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private PurchaseDocumentType type;
    @Column(name = "document_number", nullable = false, length = 40, updatable = false)
    private String number;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseDocumentStatus status = PurchaseDocumentStatus.DRAFT;
    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;
    @Column(name = "supplier_code_snapshot", nullable = false, length = 40)
    private String supplierCodeSnapshot;
    @Column(name = "supplier_name_snapshot", nullable = false, length = 180)
    private String supplierNameSnapshot;
    @Column(name = "supplier_tax_id_snapshot", length = 30)
    private String supplierTaxIdSnapshot;
    @Column(name = "supplier_reference", length = 80)
    private String supplierReference;
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;
    @Column(name = "expected_date")
    private LocalDate expectedDate;
    @Column(name = "warehouse_id")
    private UUID warehouseId;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "source_document_id", updatable = false)
    private UUID sourceDocumentId;
    @Column(name = "stock_received_upstream", nullable = false, updatable = false)
    private boolean stockReceivedUpstream;
    @Column(name = "stock_posted", nullable = false)
    private boolean stockPosted;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(length = 1000)
    private String notes;

    protected PurchaseDocument() {
    }

    public PurchaseDocument(UUID companyId, PurchaseDocumentType type, String number, UUID sourceDocumentId,
                            boolean stockReceivedUpstream) {
        super(companyId);
        this.type = type;
        this.number = number;
        this.sourceDocumentId = sourceDocumentId;
        this.stockReceivedUpstream = stockReceivedUpstream;
    }

    public void updateHeader(UUID supplierId, String supplierCodeSnapshot, String supplierNameSnapshot,
                             String supplierTaxIdSnapshot, String supplierReference, LocalDate issueDate,
                             LocalDate expectedDate, UUID warehouseId, String currencyCode, String notes) {
        requireStatus(PurchaseDocumentStatus.DRAFT, "Solo se puede modificar un documento en borrador.");
        this.supplierId = supplierId;
        this.supplierCodeSnapshot = supplierCodeSnapshot;
        this.supplierNameSnapshot = supplierNameSnapshot;
        this.supplierTaxIdSnapshot = supplierTaxIdSnapshot;
        this.supplierReference = supplierReference;
        this.issueDate = issueDate;
        this.expectedDate = expectedDate;
        this.warehouseId = warehouseId;
        this.currencyCode = currencyCode;
        this.notes = notes;
    }

    public void applyTotals(PurchaseAmounts.Totals totals) {
        requireStatus(PurchaseDocumentStatus.DRAFT, "Solo se puede modificar un documento en borrador.");
        this.netAmount = totals.net();
        this.taxAmount = totals.tax();
        this.totalAmount = totals.total();
    }

    public void confirm(boolean stockPosted) {
        requireStatus(PurchaseDocumentStatus.DRAFT, "Solo se puede confirmar un documento en borrador.");
        this.status = PurchaseDocumentStatus.CONFIRMED;
        this.stockPosted = stockPosted;
    }

    public void markConverted() {
        requireStatus(PurchaseDocumentStatus.CONFIRMED, "Solo se puede convertir un documento confirmado.");
        this.status = PurchaseDocumentStatus.CONVERTED;
    }

    /** Devuelve el documento a confirmado cuando se descarta el documento al que se había convertido. */
    public void reopenAfterDiscardedConversion() {
        if (status == PurchaseDocumentStatus.CONVERTED) {
            this.status = PurchaseDocumentStatus.CONFIRMED;
        }
    }

    public void cancel() {
        if (status != PurchaseDocumentStatus.DRAFT && status != PurchaseDocumentStatus.CONFIRMED) {
            throw new IllegalStateException(status == PurchaseDocumentStatus.CONVERTED
                    ? "El documento ya se convirtió. Anula primero el documento al que dio origen."
                    : "El documento ya está anulado.");
        }
        this.status = PurchaseDocumentStatus.CANCELLED;
        this.stockPosted = false;
    }

    private void requireStatus(PurchaseDocumentStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    public PurchaseDocumentType getType() { return type; }
    public String getNumber() { return number; }
    public PurchaseDocumentStatus getStatus() { return status; }
    public UUID getSupplierId() { return supplierId; }
    public String getSupplierCodeSnapshot() { return supplierCodeSnapshot; }
    public String getSupplierNameSnapshot() { return supplierNameSnapshot; }
    public String getSupplierTaxIdSnapshot() { return supplierTaxIdSnapshot; }
    public String getSupplierReference() { return supplierReference; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getCurrencyCode() { return currencyCode; }
    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public boolean isStockReceivedUpstream() { return stockReceivedUpstream; }
    public boolean isStockPosted() { return stockPosted; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getNotes() { return notes; }
}
