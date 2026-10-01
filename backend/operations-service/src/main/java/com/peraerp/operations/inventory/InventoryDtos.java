package com.peraerp.operations.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record WarehouseRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String location,
            Boolean defaultWarehouse,
            Boolean active
    ) {
    }

    public record WarehouseResponse(UUID id, String code, String name, String location, boolean defaultWarehouse,
                                    boolean active, Instant createdAt, Instant updatedAt) {
        static WarehouseResponse from(Warehouse warehouse) {
            return new WarehouseResponse(warehouse.getId(), warehouse.getCode(), warehouse.getName(),
                    warehouse.getLocation(), warehouse.isDefaultWarehouse(), warehouse.isActive(),
                    warehouse.getCreatedAt(), warehouse.getUpdatedAt());
        }
    }

    public record StockLevelResponse(UUID id, UUID warehouseId, UUID productId, String productCode,
                                     String productName, String unitOfMeasure, BigDecimal quantity,
                                     Instant updatedAt) {
        static StockLevelResponse from(StockLevel level) {
            return new StockLevelResponse(level.getId(), level.getWarehouseId(), level.getProductId(),
                    level.getProductCodeSnapshot(), level.getProductNameSnapshot(),
                    level.getUnitOfMeasureSnapshot(), level.getQuantity(), level.getUpdatedAt());
        }
    }

    public record StockMovementResponse(UUID id, UUID warehouseId, UUID productId, String productCode,
                                        String productName, String unitOfMeasure, StockMovementType type,
                                        BigDecimal quantity, BigDecimal balanceAfter, BigDecimal unitCost,
                                        String costCurrencyCode, Instant occurredAt, StockSourceType sourceType,
                                        UUID sourceId, String sourceNumber, String note) {
        static StockMovementResponse from(StockMovement movement) {
            return new StockMovementResponse(movement.getId(), movement.getWarehouseId(), movement.getProductId(),
                    movement.getProductCodeSnapshot(), movement.getProductNameSnapshot(),
                    movement.getUnitOfMeasureSnapshot(), movement.getType(), movement.getQuantity(),
                    movement.getBalanceAfter(), movement.getUnitCost(), movement.getCostCurrencyCode(),
                    movement.getOccurredAt(), movement.getSourceType(), movement.getSourceId(),
                    movement.getSourceNumberSnapshot(), movement.getNote());
        }
    }

    /** Ajuste manual de existencias. El motivo es obligatorio porque no hay documento que lo explique. */
    public record StockAdjustmentRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID productId,
            @NotBlank @Size(max = 100) String productCode,
            @NotBlank @Size(max = 300) String productName,
            @NotBlank @Size(max = 30) String unitOfMeasure,
            @NotNull StockMovementType type,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal quantity,
            @NotBlank @Size(max = 500) String note
    ) {
    }

    public record StockTransferRequest(
            @NotNull UUID sourceWarehouseId,
            @NotNull UUID targetWarehouseId,
            @NotNull UUID productId,
            @NotBlank @Size(max = 100) String productCode,
            @NotBlank @Size(max = 300) String productName,
            @NotBlank @Size(max = 30) String unitOfMeasure,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal quantity,
            @Size(max = 500) String note
    ) {
    }
}
