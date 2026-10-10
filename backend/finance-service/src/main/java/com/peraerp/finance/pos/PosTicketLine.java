package com.peraerp.finance.pos;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Línea de un ticket. El precio unitario lleva impuestos incluidos, como se ve en la tienda; la
 * base y la cuota se calculan por tipo impositivo sobre el total del ticket.
 */
@Entity
@Table(name = "pos_ticket_lines")
public class PosTicketLine extends CompanyScopedEntity {

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;
    @Column(name = "line_number", nullable = false, updatable = false)
    private int lineNumber;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "product_code", nullable = false, length = 60, updatable = false)
    private String productCode;
    @Column(name = "product_name", nullable = false, length = 180, updatable = false)
    private String productName;
    @Column(length = 64, updatable = false)
    private String barcode;
    @Column(name = "unit_of_measure", nullable = false, length = 30, updatable = false)
    private String unitOfMeasure;
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal unitPrice;
    @Column(name = "price_overridden", nullable = false, updatable = false)
    private boolean priceOverridden;
    @Column(name = "discount_percent", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal discountPercent;
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4, updatable = false)
    private BigDecimal taxRate;
    @Column(name = "line_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal lineTotal;
    /** Cantidad e importe ya devueltos de esta línea de venta. */
    @Column(name = "refunded_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedQuantity = BigDecimal.ZERO;
    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    @Column(name = "original_line_id", updatable = false)
    private UUID originalLineId;

    protected PosTicketLine() {
    }

    public PosTicketLine(UUID companyId, UUID ticketId, int lineNumber, PosCalculator.LineInput input,
                         BigDecimal lineTotal, UUID originalLineId) {
        super(companyId);
        this.ticketId = ticketId;
        this.lineNumber = lineNumber;
        this.productId = input.productId();
        this.productCode = input.productCode();
        this.productName = input.productName();
        this.barcode = input.barcode();
        this.unitOfMeasure = input.unitOfMeasure();
        this.quantity = input.quantity();
        this.unitPrice = input.unitPrice();
        this.priceOverridden = input.priceOverridden();
        this.discountPercent = input.discountPercent();
        this.taxRate = input.taxRate();
        this.lineTotal = lineTotal;
        this.originalLineId = originalLineId;
    }

    public BigDecimal refundableQuantity() {
        return quantity.subtract(refundedQuantity);
    }

    /**
     * Importe que corresponde a devolver {@code returned} unidades. La última devolución se lleva lo
     * que queda, para que la suma de devoluciones nunca difiera del importe cobrado por redondeo.
     */
    public BigDecimal refundAmountFor(BigDecimal returned) {
        if (returned.compareTo(refundableQuantity()) == 0) {
            return lineTotal.subtract(refundedAmount);
        }
        return PosCalculator.money(lineTotal.multiply(returned).divide(quantity, 8, java.math.RoundingMode.HALF_UP));
    }

    public void registerRefund(BigDecimal returnedQuantity, BigDecimal returnedAmount) {
        if (returnedQuantity.compareTo(refundableQuantity()) > 0) {
            throw new IllegalStateException("No se pueden devolver más unidades de " + productCode + " de las vendidas.");
        }
        this.refundedQuantity = refundedQuantity.add(returnedQuantity);
        this.refundedAmount = refundedAmount.add(returnedAmount);
    }

    public UUID getTicketId() { return ticketId; }
    public int getLineNumber() { return lineNumber; }
    public UUID getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public String getBarcode() { return barcode; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public boolean isPriceOverridden() { return priceOverridden; }
    public BigDecimal getDiscountPercent() { return discountPercent; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getLineTotal() { return lineTotal; }
    public BigDecimal getRefundedQuantity() { return refundedQuantity; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public UUID getOriginalLineId() { return originalLineId; }
}
