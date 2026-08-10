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
    @Column(name = "requested_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal requestedQuantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;
    @Column(name = "discount_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal discountPercentage = BigDecimal.ZERO;
    @Column(name = "tax_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal taxPercentage = BigDecimal.ZERO;
    @Column(name = "tax_code_id")
    private UUID taxCodeId;
    @Column(name = "tax_code_snapshot", length = 40)
    private String taxCodeSnapshot;
    @Column(name = "tax_country_code_snapshot", length = 2)
    private String taxCountryCodeSnapshot;
    @Column(name = "tax_name_snapshot", length = 140)
    private String taxNameSnapshot;
    @Column(name = "tax_exempt_snapshot")
    private Boolean taxExemptSnapshot;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal netAmount = BigDecimal.ZERO;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "tariff_id")
    private UUID tariffId;
    @Column(name = "tariff_code_snapshot", length = 60)
    private String tariffCodeSnapshot;
    @Column(name = "pricing_resolved_amount", precision = 19, scale = 4)
    private BigDecimal pricingResolvedAmount;
    @Column(name = "pricing_trace_json", columnDefinition = "TEXT")
    private String pricingTraceJson;

    protected DocumentLine() {}
    public DocumentLine(UUID productId, String productCodeSnapshot, String description, BigDecimal quantity,
                        BigDecimal unitPrice, BigDecimal discountPercentage, BigDecimal taxPercentage) {
        this(productId, productCodeSnapshot, description, quantity, quantity, unitPrice, discountPercentage,
                taxPercentage, null, null, null, null);
    }
    public DocumentLine(UUID productId, String productCodeSnapshot, String description,
                        BigDecimal requestedQuantity, BigDecimal billedQuantity, BigDecimal unitPrice,
                        BigDecimal discountPercentage, BigDecimal taxPercentage, UUID tariffId,
                        String tariffCodeSnapshot, BigDecimal pricingResolvedAmount, String pricingTraceJson) {
        this(productId, productCodeSnapshot, description, requestedQuantity, billedQuantity, unitPrice,
                discountPercentage, taxPercentage, tariffId, tariffCodeSnapshot, pricingResolvedAmount,
                pricingTraceJson, null, null, null, null, null);
    }
    public DocumentLine(UUID productId, String productCodeSnapshot, String description,
                        BigDecimal requestedQuantity, BigDecimal billedQuantity, BigDecimal unitPrice,
                        BigDecimal discountPercentage, BigDecimal taxPercentage, UUID tariffId,
                        String tariffCodeSnapshot, BigDecimal pricingResolvedAmount, String pricingTraceJson,
                        UUID taxCodeId, String taxCodeSnapshot, String taxCountryCodeSnapshot,
                        String taxNameSnapshot, Boolean taxExemptSnapshot) {
        this.productId=productId; this.productCodeSnapshot=productCodeSnapshot; this.description=description;
        this.requestedQuantity=requestedQuantity; this.quantity=billedQuantity; this.unitPrice=unitPrice;
        this.discountPercentage=discountPercentage == null ? BigDecimal.ZERO : discountPercentage;
        this.taxPercentage=taxPercentage == null ? BigDecimal.ZERO : taxPercentage;
        this.tariffId=tariffId; this.tariffCodeSnapshot=tariffCodeSnapshot;
        this.pricingResolvedAmount=pricingResolvedAmount; this.pricingTraceJson=pricingTraceJson;
        this.taxCodeId=taxCodeId; this.taxCodeSnapshot=taxCodeSnapshot;
        this.taxCountryCodeSnapshot=taxCountryCodeSnapshot; this.taxNameSnapshot=taxNameSnapshot;
        this.taxExemptSnapshot=taxExemptSnapshot;
    }
    void attachTo(CommercialDocument document, int order) { this.document=document; this.lineOrder=order; }
    void recalculate(DocumentAmountsCalculator calculator) {
        LineAmounts amounts = pricingResolvedAmount == null
                ? calculator.calculate(quantity, unitPrice, discountPercentage, taxPercentage)
                : calculator.calculate(BigDecimal.ONE, pricingResolvedAmount, discountPercentage, taxPercentage);
        netAmount=amounts.net(); taxAmount=amounts.tax(); totalAmount=amounts.total();
    }
    DocumentLine copySnapshot() {
        return new DocumentLine(productId, productCodeSnapshot, description, requestedQuantity, quantity, unitPrice,
                discountPercentage, taxPercentage, tariffId, tariffCodeSnapshot, pricingResolvedAmount,
                pricingTraceJson, taxCodeId, taxCodeSnapshot, taxCountryCodeSnapshot, taxNameSnapshot,
                taxExemptSnapshot);
    }
    public int getLineOrder() { return lineOrder; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public BigDecimal getTaxPercentage() { return taxPercentage; }
    public UUID getTaxCodeId() { return taxCodeId; }
    public String getTaxCodeSnapshot() { return taxCodeSnapshot; }
    public String getTaxCountryCodeSnapshot() { return taxCountryCodeSnapshot; }
    public String getTaxNameSnapshot() { return taxNameSnapshot; }
    public Boolean getTaxExemptSnapshot() { return taxExemptSnapshot; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public UUID getTariffId() { return tariffId; }
    public String getTariffCodeSnapshot() { return tariffCodeSnapshot; }
    public BigDecimal getPricingResolvedAmount() { return pricingResolvedAmount; }
    public String getPricingTraceJson() { return pricingTraceJson; }
}
