package com.peraerp.operations.purchasing;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_document_lines", uniqueConstraints = @UniqueConstraint(
        name = "uk_purchase_line_sequence", columnNames = {"company_id", "document_id", "line_sequence"}))
public class PurchaseDocumentLine extends CompanyScopedEntity {

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Column(name = "line_sequence", nullable = false)
    private int lineSequence;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "product_code_snapshot", length = 100)
    private String productCodeSnapshot;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(name = "unit_of_measure_snapshot", nullable = false, length = 30)
    private String unitOfMeasureSnapshot;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitPrice;
    @Column(name = "discount_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal discountPercentage;
    @Column(name = "tax_percentage", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxPercentage;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount;

    protected PurchaseDocumentLine() {
    }

    public PurchaseDocumentLine(UUID companyId, UUID documentId, int lineSequence, UUID productId,
                                String productCodeSnapshot, String description, String unitOfMeasureSnapshot,
                                BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercentage,
                                BigDecimal taxPercentage, BigDecimal netAmount) {
        super(companyId);
        this.documentId = documentId;
        this.lineSequence = lineSequence;
        this.productId = productId;
        this.productCodeSnapshot = productCodeSnapshot;
        this.description = description;
        this.unitOfMeasureSnapshot = unitOfMeasureSnapshot;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountPercentage = discountPercentage;
        this.taxPercentage = taxPercentage;
        this.netAmount = netAmount;
    }

    public UUID getDocumentId() { return documentId; }
    public int getLineSequence() { return lineSequence; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getDescription() { return description; }
    public String getUnitOfMeasureSnapshot() { return unitOfMeasureSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public BigDecimal getNetAmount() { return netAmount; }
}
