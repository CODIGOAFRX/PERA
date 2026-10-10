package com.peraerp.operations.inventory;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Apunte del diario de almacén. Es inmutable: no expone ningún método que lo modifique. */
@Entity
@Table(name = "stock_movements")
public class StockMovement extends CompanyScopedEntity {

    @Column(name = "warehouse_id", nullable = false, updatable = false)
    private UUID warehouseId;
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;
    @Column(name = "product_code_snapshot", nullable = false, length = 100, updatable = false)
    private String productCodeSnapshot;
    @Column(name = "product_name_snapshot", nullable = false, length = 300, updatable = false)
    private String productNameSnapshot;
    @Column(name = "unit_of_measure_snapshot", nullable = false, length = 30, updatable = false)
    private String unitOfMeasureSnapshot;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30, updatable = false)
    private StockMovementType type;
    @Column(nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal quantity;
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal balanceAfter;
    @Column(name = "unit_cost", precision = 19, scale = 6, updatable = false)
    private BigDecimal unitCost;
    @Column(name = "cost_currency_code", length = 3, updatable = false)
    private String costCurrencyCode;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40, updatable = false)
    private StockSourceType sourceType;
    @Column(name = "source_id", updatable = false)
    private UUID sourceId;
    @Column(name = "source_number_snapshot", length = 100, updatable = false)
    private String sourceNumberSnapshot;
    @Column(length = 500, updatable = false)
    private String note;
    @Column(name = "performed_by", updatable = false)
    private UUID performedBy;
    @Column(name = "performed_by_name", length = 160, updatable = false)
    private String performedByName;
    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 30, updatable = false)
    private StockReason reasonCode;
    @Column(name = "client_reference", updatable = false)
    private UUID clientReference;

    protected StockMovement() {
    }

    public StockMovement(StockLevel level, StockMovementType type, BigDecimal quantity, BigDecimal balanceAfter,
                         BigDecimal unitCost, String costCurrencyCode, Instant occurredAt,
                         StockSourceType sourceType, UUID sourceId, String sourceNumberSnapshot, String note) {
        super(level.getCompanyId());
        this.warehouseId = level.getWarehouseId();
        this.productId = level.getProductId();
        this.productCodeSnapshot = level.getProductCodeSnapshot();
        this.productNameSnapshot = level.getProductNameSnapshot();
        this.unitOfMeasureSnapshot = level.getUnitOfMeasureSnapshot();
        this.type = type;
        this.quantity = quantity;
        this.balanceAfter = balanceAfter;
        this.unitCost = unitCost;
        this.costCurrencyCode = costCurrencyCode;
        this.occurredAt = occurredAt;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceNumberSnapshot = sourceNumberSnapshot;
        this.note = note;
    }

    /** Autor, motivo y referencia del dispositivo. Solo se fijan al crear el apunte. */
    public StockMovement attribute(UUID performedBy, String performedByName, StockReason reasonCode,
                                   UUID clientReference) {
        this.performedBy = performedBy;
        this.performedByName = performedByName;
        this.reasonCode = reasonCode;
        this.clientReference = clientReference;
        return this;
    }

    public UUID getWarehouseId() { return warehouseId; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public String getUnitOfMeasureSnapshot() { return unitOfMeasureSnapshot; }
    public StockMovementType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getCostCurrencyCode() { return costCurrencyCode; }
    public Instant getOccurredAt() { return occurredAt; }
    public StockSourceType getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getSourceNumberSnapshot() { return sourceNumberSnapshot; }
    public String getNote() { return note; }
    public UUID getPerformedBy() { return performedBy; }
    public String getPerformedByName() { return performedByName; }
    public StockReason getReasonCode() { return reasonCode; }
    public UUID getClientReference() { return clientReference; }
}
