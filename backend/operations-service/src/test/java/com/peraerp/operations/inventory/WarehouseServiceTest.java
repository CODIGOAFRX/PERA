package com.peraerp.operations.inventory;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.inventory.InventoryDtos.WarehouseRequest;
import com.peraerp.operations.inventory.InventoryDtos.WarehouseResponse;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock WarehouseRepository repository;
    @Mock StockMovementRepository movementRepository;
    @Mock CurrentCompanyProvider companyProvider;

    private WarehouseService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new WarehouseService(repository, movementRepository, companyProvider);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void normalizesTheCodeAndRejectsDuplicates() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "MAIN")).thenReturn(false, true);
        when(repository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse created = service.create(request(" main ", false, true));

        assertThat(created.code()).isEqualTo("MAIN");
        assertThat(created.active()).isTrue();
        assertThatThrownBy(() -> service.create(request("MAIN", false, true)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void makingAWarehouseDefaultClearsThePreviousOneBeforeSaving() {
        Warehouse previous = warehouse("OLD");
        previous.update("Antiguo", null, true, true);
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "NEW")).thenReturn(false);
        when(repository.findByCompanyIdAndDefaultWarehouseTrue(companyId)).thenReturn(Optional.of(previous));
        when(repository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse created = service.create(request("NEW", true, true));

        assertThat(created.defaultWarehouse()).isTrue();
        assertThat(previous.isDefaultWarehouse()).isFalse();
        InOrder order = inOrder(repository);
        order.verify(repository).flush();
        order.verify(repository).save(any(Warehouse.class));
    }

    @Test
    void theDefaultWarehouseMustBeActive() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "NEW")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("NEW", true, false)))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void theCodeCannotChangeAndAWarehouseWithMovementsCannotBeDeleted() {
        Warehouse warehouse = warehouse("MAIN");
        when(repository.findByIdAndCompanyId(warehouse.getId(), companyId)).thenReturn(Optional.of(warehouse));
        when(movementRepository.existsByCompanyIdAndWarehouseId(companyId, warehouse.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.update(warehouse.getId(), request("OTHER", false, true)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.delete(warehouse.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Desactívalo");
        verify(repository, never()).delete(any());
    }

    private WarehouseRequest request(String code, boolean defaultWarehouse, boolean active) {
        return new WarehouseRequest(code, "Almacén " + code.trim(), null, defaultWarehouse, active);
    }

    private Warehouse warehouse(String code) {
        Warehouse warehouse = new Warehouse(companyId, code, code);
        ReflectionTestUtils.setField(warehouse, "id", UUID.randomUUID());
        return warehouse;
    }
}
