package com.peraerp.operations.salesdelivery;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sales_delivery_lines", uniqueConstraints = @UniqueConstraint(
        name = "uk_sales_delivery_line_sequence", columnNames = {"company_id", "delivery_id", "line_sequence"}))
public class SalesDeliveryLine extends CompanyScopedEntity {

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;
    @Column(name = "line_sequence", nullable = false)
    private int lineSequence;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "product_code_snapshot", nullable = false, length = 100)
    private String productCodeSnapshot;
    @Column(nullable = false, length = 300)
    private String description;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    protected SalesDeliveryLine() {
    }

    public SalesDeliveryLine(UUID companyId, UUID deliveryId, int lineSequence, UUID productId,
                             String productCodeSnapshot, String description, BigDecimal quantity) {
        super(companyId);
        this.deliveryId = deliveryId;
        this.lineSequence = lineSequence;
        this.productId = productId;
        this.productCodeSnapshot = productCodeSnapshot;
        this.description = description;
        this.quantity = quantity;
    }

    public UUID getDeliveryId() { return deliveryId; }
    public int getLineSequence() { return lineSequence; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getDescription() { return description; }
    public BigDecimal getQuantity() { return quantity; }
}
