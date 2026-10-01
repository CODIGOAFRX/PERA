package com.peraerp.operations.inventory;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_levels", uniqueConstraints = @UniqueConstraint(
        name = "uk_stock_level_warehouse_product", columnNames = {"company_id", "warehouse_id", "product_id"}))
public class StockLevel extends CompanyScopedEntity {

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "product_code_snapshot", nullable = false, length = 100)
    private String productCodeSnapshot;
    @Column(name = "product_name_snapshot", nullable = false, length = 300)
    private String productNameSnapshot;
    @Column(name = "unit_of_measure_snapshot", nullable = false, length = 30, updatable = false)
    private String unitOfMeasureSnapshot;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity = BigDecimal.ZERO;

    protected StockLevel() {
    }

    public StockLevel(UUID companyId, UUID warehouseId, UUID productId, String productCodeSnapshot,
                      String productNameSnapshot, String unitOfMeasureSnapshot) {
        super(companyId);
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.productCodeSnapshot = productCodeSnapshot;
        this.productNameSnapshot = productNameSnapshot;
        this.unitOfMeasureSnapshot = unitOfMeasureSnapshot;
    }

    /** Aplica un movimiento y devuelve la existencia resultante. */
    public BigDecimal apply(StockMovementType type, BigDecimal movementQuantity) {
        BigDecimal next = type.isInbound() ? quantity.add(movementQuantity) : quantity.subtract(movementQuantity);
        if (next.signum() < 0) {
            throw new IllegalStateException("No hay existencias suficientes de " + productCodeSnapshot
                    + ": disponibles " + quantity.stripTrailingZeros().toPlainString()
                    + ", solicitadas " + movementQuantity.stripTrailingZeros().toPlainString() + ".");
        }
        this.quantity = next;
        return next;
    }

    public void refreshProductSnapshot(String productCodeSnapshot, String productNameSnapshot) {
        this.productCodeSnapshot = productCodeSnapshot;
        this.productNameSnapshot = productNameSnapshot;
    }

    public UUID getWarehouseId() { return warehouseId; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getUnitOfMeasureSnapshot() { return unitOfMeasureSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
}
