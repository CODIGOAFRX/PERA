package com.peraerp.operations.inventory;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.config.CurrentUserProvider;
import com.peraerp.operations.config.CurrentUserProvider.CurrentUser;
import com.peraerp.operations.inventory.InventoryDtos.ScanDirection;
import com.peraerp.operations.inventory.InventoryDtos.StockMovementResponse;
import com.peraerp.operations.inventory.InventoryDtos.StockScanRequest;
import com.peraerp.operations.inventory.InventoryDtos.StockLevelResponse;
import com.peraerp.operations.inventory.InventoryDtos.StockLocationRequest;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Entradas y retiradas desde la aplicación de almacén del móvil. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockScanTest {

    @Mock StockLevelRepository levelRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock CurrentUserProvider userProvider;

    private final Map<String, StockLevel> levels = new HashMap<>();
    private final List<StockMovement> movements = new ArrayList<>();
    private StockService service;
    private UUID companyId;
    private UUID productId;
    private UUID userId;
    private Warehouse main;

    @BeforeEach
    void setUp() {
        service = new StockService(levelRepository, movementRepository, warehouseRepository, companyProvider,
                userProvider);
        companyId = UUID.randomUUID();
        productId = UUID.randomUUID();
        userId = UUID.randomUUID();
        main = new Warehouse(companyId, "MAIN", "Principal");
        ReflectionTestUtils.setField(main, "id", UUID.randomUUID());
        main.update("Principal", null, true, true);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(userProvider.findUser()).thenReturn(Optional.of(new CurrentUser(userId, "Iván · Almacén")));
        when(userProvider.requireUser()).thenReturn(new CurrentUser(userId, "Iván · Almacén"));
        when(warehouseRepository.findByIdAndCompanyId(main.getId(), companyId)).thenReturn(Optional.of(main));
        when(warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(companyId)).thenReturn(Optional.of(main));
        when(levelRepository.findForUpdate(eq(companyId), any(), any())).thenAnswer(invocation ->
                Optional.ofNullable(levels.get(invocation.getArgument(1) + "/" + invocation.getArgument(2))));
        when(levelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> {
            StockLevel level = invocation.getArgument(0);
            levels.put(level.getWarehouseId() + "/" + level.getProductId(), level);
            return level;
        });
        when(movementRepository.save(any(StockMovement.class))).thenAnswer(invocation -> {
            StockMovement movement = invocation.getArgument(0);
            movements.add(movement);
            return movement;
        });
        when(movementRepository.findByCompanyIdAndClientReference(eq(companyId), any())).thenAnswer(invocation ->
                movements.stream().filter(movement -> invocation.getArgument(1).equals(movement.getClientReference()))
                        .findFirst());
    }

    @Test
    void anEntryGoesToTheDefaultWarehouseWithItsAuthorAndReason() {
        StockMovementResponse response = service.scan(scan(ScanDirection.IN, "24", StockReason.RECEIPT, null));

        assertThat(response.type()).isEqualTo(StockMovementType.ADJUSTMENT_IN);
        assertThat(response.warehouseId()).isEqualTo(main.getId());
        assertThat(response.balanceAfter()).isEqualByComparingTo("24");
        assertThat(response.sourceType()).isEqualTo(StockSourceType.SCAN);
        assertThat(response.reasonCode()).isEqualTo(StockReason.RECEIPT);
        assertThat(response.performedByName()).isEqualTo("Iván · Almacén");
        assertThat(movements.getFirst().getPerformedBy()).isEqualTo(userId);
    }

    @Test
    void aWithdrawalCannotLeaveNegativeStockAndNeedsAWithdrawalReason() {
        service.scan(scan(ScanDirection.IN, "5", StockReason.RECEIPT, null));

        StockMovementResponse broken = service.scan(scan(ScanDirection.OUT, "2", StockReason.DAMAGED, null));
        assertThat(broken.balanceAfter()).isEqualByComparingTo("3");
        assertThatThrownBy(() -> service.scan(scan(ScanDirection.OUT, "4", StockReason.EXPIRED, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.scan(scan(ScanDirection.OUT, "1", StockReason.RECEIPT, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("retirada");
        assertThatThrownBy(() -> service.scan(scan(ScanDirection.IN, "1", StockReason.DAMAGED, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("entrada");
    }

    @Test
    void aRetriedScanWithTheSameReferenceIsNotCountedTwice() {
        UUID reference = UUID.randomUUID();
        StockMovementResponse first = service.scan(scan(ScanDirection.IN, "10", StockReason.COUNT, reference));
        StockMovementResponse retry = service.scan(scan(ScanDirection.IN, "10", StockReason.COUNT, reference));

        assertThat(retry.balanceAfter()).isEqualByComparingTo(first.balanceAfter());
        verify(movementRepository, times(1)).save(any(StockMovement.class));
    }

    @Test
    void withoutADefaultWarehouseTheOperatorMustChooseOne() {
        when(warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.scan(scan(ScanDirection.IN, "1", StockReason.RECEIPT, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("predeterminado");
    }

    @Test
    void theLocationCanBeSetBeforeTheGoodsArriveAndChangedWithoutTouchingTheStock() {
        StockLevelResponse empty = service.changeLocation(location("Cámara frigorífica 2"));
        assertThat(empty.quantity()).isEqualByComparingTo("0");
        assertThat(empty.location()).isEqualTo("Cámara frigorífica 2");

        service.scan(scan(ScanDirection.IN, "12", StockReason.RECEIPT, null));
        StockLevelResponse moved = service.changeLocation(location("  Pasillo 3 · Estante B  "));
        assertThat(moved.quantity()).isEqualByComparingTo("12");
        assertThat(moved.location()).isEqualTo("Pasillo 3 · Estante B");

        assertThat(service.changeLocation(location("  ")).location()).isNull();
        verify(movementRepository, times(1)).save(any(StockMovement.class));
    }

    private StockLocationRequest location(String value) {
        return new StockLocationRequest(main.getId(), productId, "SARD-120", "Sardinas en aceite 120 g", "UNIT", value);
    }

    private StockScanRequest scan(ScanDirection direction, String quantity, StockReason reason, UUID reference) {
        return new StockScanRequest(null, productId, "SARD-120", "Sardinas en aceite 120 g", "UNIT", direction,
                new BigDecimal(quantity), reason, null, reference);
    }
}
