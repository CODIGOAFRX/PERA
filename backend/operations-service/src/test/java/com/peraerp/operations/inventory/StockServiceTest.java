package com.peraerp.operations.inventory;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.inventory.InventoryDtos.StockAdjustmentRequest;
import com.peraerp.operations.inventory.InventoryDtos.StockMovementResponse;
import com.peraerp.operations.inventory.InventoryDtos.StockTransferRequest;
import com.peraerp.operations.inventory.StockService.StockPosting;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockServiceTest {

    @Mock StockLevelRepository levelRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock CurrentCompanyProvider companyProvider;

    private final Map<String, StockLevel> levels = new HashMap<>();
    private StockService service;
    private UUID companyId;
    private UUID productId;
    private Warehouse main;
    private Warehouse secondary;

    @BeforeEach
    void setUp() {
        service = new StockService(levelRepository, movementRepository, warehouseRepository, companyProvider);
        companyId = UUID.randomUUID();
        productId = UUID.randomUUID();
        main = warehouse("MAIN");
        secondary = warehouse("SEC");
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        // Repositorio en memoria: lo que se guarda es lo que se encuentra en la siguiente lectura.
        when(levelRepository.findForUpdate(eq(companyId), any(), any())).thenAnswer(invocation ->
                Optional.ofNullable(levels.get(invocation.getArgument(1) + "/" + invocation.getArgument(2))));
        when(levelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> {
            StockLevel level = invocation.getArgument(0);
            levels.put(level.getWarehouseId() + "/" + level.getProductId(), level);
            return level;
        });
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void anInboundPostingCreatesTheLevelAndRecordsTheRunningBalance() {
        StockMovement first = service.post(companyId, receipt(main, "10", "4.50"));
        StockMovement second = service.post(companyId, receipt(main, "2.5", "5"));

        assertThat(first.getBalanceAfter()).isEqualByComparingTo("10");
        assertThat(second.getBalanceAfter()).isEqualByComparingTo("12.5");
        assertThat(second.getUnitCost()).isEqualByComparingTo("5");
        assertThat(second.getCostCurrencyCode()).isEqualTo("EUR");
        assertThat(second.getSourceType()).isEqualTo(StockSourceType.PURCHASE_DOCUMENT);
        assertThat(level(main).getQuantity()).isEqualByComparingTo("12.5");
    }

    @Test
    void anOutboundPostingCannotLeaveNegativeStock() {
        service.post(companyId, receipt(main, "3", "1"));

        assertThatThrownBy(() -> service.adjust(adjustment(main, StockMovementType.ADJUSTMENT_OUT, "3.000001")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("No hay existencias suficientes");
        assertThat(level(main).getQuantity()).isEqualByComparingTo("3");

        StockMovementResponse exact = service.adjust(adjustment(main, StockMovementType.ADJUSTMENT_OUT, "3"));
        assertThat(exact.balanceAfter()).isEqualByComparingTo("0");
    }

    @Test
    void anOutboundPostingOfAProductThatWasNeverReceivedIsRejected() {
        assertThatThrownBy(() -> service.adjust(adjustment(main, StockMovementType.ADJUSTMENT_OUT, "1")))
                .isInstanceOf(BusinessRuleException.class);
        verify(movementRepository, never()).save(any());
    }

    @Test
    void rejectsAMovementInADifferentUnitThanTheStockIsKeptIn() {
        service.post(companyId, receipt(main, "1", "1"));
        StockPosting inKilograms = new StockPosting(main.getId(), productId, "P-1", "Producto", "KILOGRAM",
                StockMovementType.ADJUSTMENT_IN, BigDecimal.ONE, null, null, StockSourceType.MANUAL, null, null,
                "Recuento");

        assertThatThrownBy(() -> service.post(companyId, inKilograms))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("UNIT");
    }

    @Test
    void rejectsInactiveOrForeignWarehouses() {
        main.update("Principal", null, false, false);
        assertThatThrownBy(() -> service.post(companyId, receipt(main, "1", "1")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactivo");

        UUID foreign = UUID.randomUUID();
        when(warehouseRepository.findByIdAndCompanyId(foreign, companyId)).thenReturn(Optional.empty());
        StockAdjustmentRequest request = new StockAdjustmentRequest(foreign, productId, "P-1", "Producto", "UNIT",
                StockMovementType.ADJUSTMENT_IN, BigDecimal.ONE, "Recuento");
        assertThatThrownBy(() -> service.adjust(request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void onlyAdjustmentsCanBeRecordedByHand() {
        assertThatThrownBy(() -> service.adjust(adjustment(main, StockMovementType.PURCHASE_RECEIPT, "1")))
                .isInstanceOf(BusinessRuleException.class);
        verify(levelRepository, never()).save(any());
    }

    @Test
    void aTransferMovesTheQuantityBetweenWarehousesAndLinksBothMovements() {
        service.post(companyId, receipt(main, "8", "1"));

        List<StockMovementResponse> movements = service.transfer(new StockTransferRequest(main.getId(),
                secondary.getId(), productId, "P-1", "Producto", "UNIT", new BigDecimal("5"), null));

        assertThat(movements).extracting(StockMovementResponse::type)
                .containsExactly(StockMovementType.TRANSFER_OUT, StockMovementType.TRANSFER_IN);
        assertThat(movements.get(0).sourceId()).isNotNull().isEqualTo(movements.get(1).sourceId());
        assertThat(level(main).getQuantity()).isEqualByComparingTo("3");
        assertThat(level(secondary).getQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void aTransferNeedsTwoDifferentWarehouses() {
        assertThatThrownBy(() -> service.transfer(new StockTransferRequest(main.getId(), main.getId(), productId,
                "P-1", "Producto", "UNIT", BigDecimal.ONE, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    private StockPosting receipt(Warehouse warehouse, String quantity, String unitCost) {
        return new StockPosting(warehouse.getId(), productId, "P-1", "Producto", "unit",
                StockMovementType.PURCHASE_RECEIPT, new BigDecimal(quantity), new BigDecimal(unitCost), "EUR",
                StockSourceType.PURCHASE_DOCUMENT, UUID.randomUUID(), "AC-2026-000001", null);
    }

    private StockAdjustmentRequest adjustment(Warehouse warehouse, StockMovementType type, String quantity) {
        return new StockAdjustmentRequest(warehouse.getId(), productId, "P-1", "Producto", "UNIT", type,
                new BigDecimal(quantity), "Recuento de inventario");
    }

    private StockLevel level(Warehouse warehouse) {
        return levels.get(warehouse.getId() + "/" + productId);
    }

    private Warehouse warehouse(String code) {
        Warehouse warehouse = new Warehouse(companyId, code, code);
        ReflectionTestUtils.setField(warehouse, "id", UUID.randomUUID());
        when(warehouseRepository.findByIdAndCompanyId(warehouse.getId(), companyId))
                .thenReturn(Optional.of(warehouse));
        return warehouse;
    }
}
