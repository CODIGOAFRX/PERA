package com.peraerp.operations.salesdelivery;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.inventory.StockLevel;
import com.peraerp.operations.inventory.StockLevelRepository;
import com.peraerp.operations.inventory.StockMovement;
import com.peraerp.operations.inventory.StockMovementRepository;
import com.peraerp.operations.inventory.StockMovementType;
import com.peraerp.operations.inventory.StockService;
import com.peraerp.operations.inventory.StockService.StockPosting;
import com.peraerp.operations.inventory.StockSourceType;
import com.peraerp.operations.inventory.Warehouse;
import com.peraerp.operations.inventory.WarehouseRepository;
import com.peraerp.operations.salesdelivery.SalesDeliveryService.SalesDeliveryResponse;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesDeliveryServiceTest {

    private static final Instant STARTED = Instant.parse("2026-10-01T08:00:00Z");

    @Mock SalesDeliveryRepository repository;
    @Mock SalesDeliveryLineRepository lineRepository;
    @Mock SalesDeliverySyncRepository syncRepository;
    @Mock SalesDeliveryClient client;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockLevelRepository levelRepository;
    @Mock StockMovementRepository movementRepository;
    @Mock StockService stockService;
    @Mock CurrentCompanyProvider companyProvider;

    private final Map<UUID, SalesDelivery> deliveries = new HashMap<>();
    private final Map<UUID, List<SalesDeliveryLine>> linesByDelivery = new HashMap<>();
    private final Map<UUID, StockLevel> levels = new HashMap<>();
    private SalesDeliveryService service;
    private UUID companyId;
    private UUID tracked;
    private UUID untracked;
    private Warehouse warehouse;
    private SalesDeliverySync sync;

    @BeforeEach
    void setUp() {
        service = new SalesDeliveryService(repository, lineRepository, syncRepository, client, warehouseRepository,
                levelRepository, movementRepository, stockService, companyProvider);
        companyId = UUID.randomUUID();
        tracked = UUID.randomUUID();
        untracked = UUID.randomUUID();
        warehouse = new Warehouse(companyId, "MAIN", "Principal");
        ReflectionTestUtils.setField(warehouse, "id", UUID.randomUUID());
        warehouse.update("Principal", null, true, true);
        sync = new SalesDeliverySync(companyId, STARTED);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(syncRepository.findByCompanyId(companyId)).thenReturn(Optional.of(sync));
        when(warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(companyId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.findByIdAndCompanyId(warehouse.getId(), companyId)).thenReturn(Optional.of(warehouse));

        // Repositorios en memoria.
        when(repository.saveAndFlush(any(SalesDelivery.class))).thenAnswer(invocation -> {
            SalesDelivery delivery = invocation.getArgument(0);
            ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
            deliveries.put(delivery.getSourceDocumentId(), delivery);
            return delivery;
        });
        when(repository.findByCompanyIdAndSourceDocumentId(eq(companyId), any()))
                .thenAnswer(invocation -> Optional.ofNullable(deliveries.get(invocation.<UUID>getArgument(1))));
        when(repository.findByIdAndCompanyId(any(), eq(companyId))).thenAnswer(invocation -> deliveries.values()
                .stream().filter(delivery -> delivery.getId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findAllByCompanyIdAndStatusOrderBySourceDateAscCreatedAtAsc(eq(companyId), any()))
                .thenAnswer(invocation -> deliveries.values().stream()
                        .filter(delivery -> delivery.getStatus() == invocation.getArgument(1)).toList());
        when(lineRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<SalesDeliveryLine> lines = invocation.getArgument(0);
            if (!lines.isEmpty()) linesByDelivery.put(lines.getFirst().getDeliveryId(), new ArrayList<>(lines));
            return lines;
        });
        when(lineRepository.findAllByCompanyIdAndDeliveryIdOrderByLineSequenceAsc(eq(companyId), any()))
                .thenAnswer(invocation -> linesByDelivery.getOrDefault(invocation.<UUID>getArgument(1), List.of()));
        doAnswer(invocation -> linesByDelivery.remove(invocation.<UUID>getArgument(1)))
                .when(lineRepository).deleteAllByCompanyIdAndDeliveryId(eq(companyId), any());
        when(levelRepository.existsByCompanyIdAndProductId(eq(companyId), any()))
                .thenAnswer(invocation -> levels.containsKey(invocation.<UUID>getArgument(1)));
        when(levelRepository.findForUpdate(eq(companyId), eq(warehouse.getId()), any()))
                .thenAnswer(invocation -> Optional.ofNullable(levels.get(invocation.<UUID>getArgument(2))));
    }

    @Test
    void theFirstSynchronizationOnlyStartsCountingSoHistoricalSalesAreNotDeducted() {
        when(syncRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());

        service.synchronize(companyId);

        verify(syncRepository).save(any(SalesDeliverySync.class));
        verify(client, never()).findUpdatedSince(any(), any());
    }

    @Test
    void postsOnlyTheProductsThatAreKeptInStockAddingUpRepeatedLines() {
        stock(tracked, "10");
        Instant updatedAt = STARTED.plusSeconds(600);
        SalesDeliverySnapshot note = snapshot("ALB-1", "CONFIRMED", updatedAt,
                line(1, tracked, "P-1", "3"), line(2, untracked, "SRV", "1"), line(3, tracked, "P-1", "2.5"));
        when(client.findUpdatedSince(eq(companyId), any())).thenReturn(Optional.of(List.of(note)));

        service.synchronize(companyId);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(client).findUpdatedSince(eq(companyId), since.capture());
        assertThat(since.getValue()).isEqualTo(STARTED.minusSeconds(120));
        ArgumentCaptor<StockPosting> posting = ArgumentCaptor.forClass(StockPosting.class);
        verify(stockService, times(1)).post(eq(companyId), posting.capture());
        assertThat(posting.getValue().type()).isEqualTo(StockMovementType.SALES_ISSUE);
        assertThat(posting.getValue().productId()).isEqualTo(tracked);
        assertThat(posting.getValue().quantity()).isEqualByComparingTo("5.5");
        assertThat(posting.getValue().warehouseId()).isEqualTo(warehouse.getId());
        assertThat(posting.getValue().unitOfMeasure()).isEqualTo("UNIT");
        assertThat(posting.getValue().sourceType()).isEqualTo(StockSourceType.SALES_DOCUMENT);
        assertThat(posting.getValue().sourceId()).isEqualTo(note.id());
        assertThat(posting.getValue().sourceNumber()).isEqualTo("ALB-1");
        SalesDelivery delivery = deliveries.get(note.id());
        assertThat(delivery.getStatus()).isEqualTo(SalesDeliveryStatus.POSTED);
        assertThat(delivery.getWarehouseId()).isEqualTo(warehouse.getId());
        assertThat(sync.getLastSourceUpdate()).isEqualTo(updatedAt);

        // Releer el mismo documento (margen de solape o conversión a factura) no vuelve a descontar.
        service.synchronize(companyId);
        verify(stockService, times(1)).post(any(), any());
    }

    @Test
    void aSaleWithoutStockKeptProductsDoesNotTouchTheWarehouse() {
        SalesDeliverySnapshot note = snapshot("ALB-2", "CONFIRMED", STARTED.plusSeconds(60),
                line(1, untracked, "SRV", "4"));
        when(client.findUpdatedSince(eq(companyId), any())).thenReturn(Optional.of(List.of(note)));

        service.synchronize(companyId);

        assertThat(deliveries.get(note.id()).getStatus()).isEqualTo(SalesDeliveryStatus.NOT_APPLICABLE);
        verify(stockService, never()).post(any(), any());
    }

    @Test
    void aShortageLeavesTheDeliveryPendingAndItGoesOutByItselfWhenStockArrives() {
        StockLevel level = stock(tracked, "2");
        SalesDeliverySnapshot note = snapshot("ALB-3", "CONFIRMED", STARTED.plusSeconds(60),
                line(1, tracked, "P-1", "5"));
        when(client.findUpdatedSince(eq(companyId), any())).thenReturn(Optional.of(List.of(note)), Optional.of(List.of()));

        service.synchronize(companyId);

        SalesDelivery delivery = deliveries.get(note.id());
        assertThat(delivery.getStatus()).isEqualTo(SalesDeliveryStatus.PENDING);
        assertThat(delivery.getProblem()).contains("MAIN").contains("P-1").contains("disponibles 2")
                .contains("necesarias 5");
        verify(stockService, never()).post(any(), any());

        level.apply(StockMovementType.PURCHASE_RECEIPT, new BigDecimal("10"));
        service.synchronize(companyId);

        assertThat(delivery.getStatus()).isEqualTo(SalesDeliveryStatus.POSTED);
        assertThat(delivery.getProblem()).isNull();
        verify(stockService, times(1)).post(any(), any());
    }

    @Test
    void withoutAnActiveDefaultWarehouseTheDeliveryWaits() {
        stock(tracked, "10");
        when(warehouseRepository.findByCompanyIdAndDefaultWarehouseTrue(companyId)).thenReturn(Optional.empty());
        SalesDeliverySnapshot note = snapshot("ALB-4", "CONFIRMED", STARTED.plusSeconds(60),
                line(1, tracked, "P-1", "1"));
        when(client.findUpdatedSince(eq(companyId), any())).thenReturn(Optional.of(List.of(note)));

        service.synchronize(companyId);

        assertThat(deliveries.get(note.id()).getStatus()).isEqualTo(SalesDeliveryStatus.PENDING);
        assertThat(deliveries.get(note.id()).getProblem()).contains("almacén predeterminado");
    }

    @Test
    void aCancelledSaleReturnsExactlyWhatWentOut() {
        stock(tracked, "10");
        SalesDeliverySnapshot note = snapshot("ALB-5", "CONFIRMED", STARTED.plusSeconds(60),
                line(1, tracked, "P-1", "4"));
        SalesDeliverySnapshot cancelled = new SalesDeliverySnapshot(note.id(), "ALB-5", "DELIVERY_NOTE", "CANCELLED",
                note.issueDate(), "C001", "Cliente", STARTED.plusSeconds(900), note.lines());
        when(client.findUpdatedSince(eq(companyId), any()))
                .thenReturn(Optional.of(List.of(note)), Optional.of(List.of(cancelled)));
        service.synchronize(companyId);
        StockMovement issue = new StockMovement(levels.get(tracked), StockMovementType.SALES_ISSUE,
                new BigDecimal("4"), new BigDecimal("6"), null, null, Instant.now(), StockSourceType.SALES_DOCUMENT,
                note.id(), "ALB-5", null);
        when(movementRepository.findAllByCompanyIdAndSourceTypeAndSourceIdAndType(companyId,
                StockSourceType.SALES_DOCUMENT, note.id(), StockMovementType.SALES_ISSUE)).thenReturn(List.of(issue));

        service.synchronize(companyId);

        ArgumentCaptor<StockPosting> posting = ArgumentCaptor.forClass(StockPosting.class);
        verify(stockService, times(2)).post(eq(companyId), posting.capture());
        assertThat(posting.getAllValues().get(1).type()).isEqualTo(StockMovementType.SALES_RETURN);
        assertThat(posting.getAllValues().get(1).quantity()).isEqualByComparingTo("4");
        assertThat(posting.getAllValues().get(1).warehouseId()).isEqualTo(warehouse.getId());
        assertThat(deliveries.get(note.id()).getStatus()).isEqualTo(SalesDeliveryStatus.REVERSED);
        assertThat(deliveries.get(note.id()).getSourceStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void aCancelledSaleThatNeverWentOutIsDismissedAndAnUnknownCancelledOneIsIgnored() {
        stock(tracked, "1");
        SalesDeliverySnapshot note = snapshot("ALB-6", "CONFIRMED", STARTED.plusSeconds(60),
                line(1, tracked, "P-1", "4"));
        SalesDeliverySnapshot cancelled = new SalesDeliverySnapshot(note.id(), "ALB-6", "DELIVERY_NOTE", "CANCELLED",
                note.issueDate(), "C001", "Cliente", STARTED.plusSeconds(900), note.lines());
        SalesDeliverySnapshot unknown = snapshot("ALB-7", "CANCELLED", STARTED.plusSeconds(950),
                line(1, tracked, "P-1", "1"));
        when(client.findUpdatedSince(eq(companyId), any()))
                .thenReturn(Optional.of(List.of(note)), Optional.of(List.of(cancelled, unknown)));

        service.synchronize(companyId);
        service.synchronize(companyId);

        assertThat(deliveries.get(note.id()).getStatus()).isEqualTo(SalesDeliveryStatus.DISMISSED);
        assertThat(deliveries).doesNotContainKey(unknown.id());
        verify(stockService, never()).post(any(), any());
    }

    @Test
    void whenSalesIsUnavailableNothingChangesAndTheNextRunReadsFromTheSamePoint() {
        when(client.findUpdatedSince(eq(companyId), any())).thenReturn(Optional.empty());

        service.synchronize(companyId);

        assertThat(sync.getLastSourceUpdate()).isEqualTo(STARTED);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void aPendingDeliveryCanBePostedFromAChosenWarehouseOrDismissedByHand() {
        stock(tracked, "1");
        SalesDeliverySnapshot note = snapshot("ALB-8", "CONFIRMED", STARTED.plusSeconds(60),
                line(1, tracked, "P-1", "3"));
        when(client.findUpdatedSince(eq(companyId), any())).thenReturn(Optional.of(List.of(note)));
        service.synchronize(companyId);
        SalesDelivery delivery = deliveries.get(note.id());

        assertThatThrownBy(() -> service.post(delivery.getId(), warehouse.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Faltan existencias");

        levels.get(tracked).apply(StockMovementType.ADJUSTMENT_IN, new BigDecimal("5"));
        SalesDeliveryResponse posted = service.post(delivery.getId(), warehouse.getId());
        assertThat(posted.status()).isEqualTo(SalesDeliveryStatus.POSTED);
        assertThat(posted.lines()).hasSize(1);
        assertThatThrownBy(() -> service.dismiss(delivery.getId())).isInstanceOf(BusinessRuleException.class);
    }

    private StockLevel stock(UUID productId, String quantity) {
        StockLevel level = new StockLevel(companyId, warehouse.getId(), productId, "P-1", "Producto", "UNIT");
        level.apply(StockMovementType.ADJUSTMENT_IN, new BigDecimal(quantity));
        levels.put(productId, level);
        return level;
    }

    private SalesDeliverySnapshot snapshot(String number, String status, Instant updatedAt,
                                           SalesDeliverySnapshot.Line... lines) {
        return new SalesDeliverySnapshot(UUID.randomUUID(), number, "DELIVERY_NOTE", status,
                LocalDate.of(2026, 10, 1), "C001", "Cliente", updatedAt, List.of(lines));
    }

    private SalesDeliverySnapshot.Line line(int order, UUID productId, String code, String quantity) {
        return new SalesDeliverySnapshot.Line(order, productId, code, "Línea " + order, new BigDecimal(quantity));
    }
}
