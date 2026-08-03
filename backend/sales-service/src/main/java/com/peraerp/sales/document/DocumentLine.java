package com.peraerp.sales.document;

import com.peraerp.platform.domain.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "document_lines")
public class DocumentLine extends AuditableEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id", nullable = false)
    private CommercialDocument document;
    @Column(name = "line_order", nullable = false)
    private int lineOrder;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "product_code_snapshot", length = 60)
    private String productCodeSnapshot;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;
    @Column(name = "discount_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal discountPercentage = BigDecimal.ZERO;
    @Column(name = "tax_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxPercentage = BigDecimal.ZERO;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    protected DocumentLine() {}
    public DocumentLine(UUID productId, String productCodeSnapshot, String description, BigDecimal quantity,
                        BigDecimal unitPrice, BigDecimal discountPercentage, BigDecimal taxPercentage) {
        this.productId=productId; this.productCodeSnapshot=productCodeSnapshot; this.description=description;
        this.quantity=quantity; this.unitPrice=unitPrice;
        this.discountPercentage=discountPercentage == null ? BigDecimal.ZERO : discountPercentage;
        this.taxPercentage=taxPercentage == null ? BigDecimal.ZERO : taxPercentage;
    }
    void attachTo(CommercialDocument document, int order) { this.document=document; this.lineOrder=order; }
    void recalculate(DocumentAmountsCalculator calculator) {
        LineAmounts amounts = calculator.calculate(quantity, unitPrice, discountPercentage, taxPercentage);
        netAmount=amounts.net(); taxAmount=amounts.tax(); totalAmount=amounts.total();
    }
    public int getLineOrder() { return lineOrder; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
