package com.peraerp.operations.purchasing;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.inventory.StockMovementType;
import com.peraerp.operations.inventory.StockService;
import com.peraerp.operations.inventory.StockService.StockPosting;
import com.peraerp.operations.inventory.StockSourceType;
import com.peraerp.operations.inventory.Warehouse;
import com.peraerp.operations.inventory.WarehouseRepository;
import com.peraerp.operations.purchasing.PurchaseDtos.ConvertPurchaseDocumentRequest;
import com.peraerp.operations.purchasing.PurchaseDtos.PurchaseDocumentRequest;
import com.peraerp.operations.purchasing.PurchaseDtos.PurchaseDocumentResponse;
import com.peraerp.operations.purchasing.PurchaseDtos.PurchaseLineRequest;
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
class PurchaseDocumentServiceTest {

    private static final LocalDate ISSUE_DATE = LocalDate.of(2026, 10, 1);

    @Mock PurchaseDocumentRepository repository;
    @Mock PurchaseDocumentLineRepository lineRepository;
    @Mock PurchaseDocumentSequenceRepository sequenceRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockService stockService;
    @Mock CurrentCompanyProvider companyProvider;

    private final Map<UUID, PurchaseDocument> documents = new HashMap<>();
    private final Map<UUID, List<PurchaseDocumentLine>> linesByDocument = new HashMap<>();
    private final Map<String, PurchaseDocumentSequence> sequences = new HashMap<>();
    private PurchaseDocumentService service;
    private UUID companyId;
    private UUID supplierId;
    private UUID productId;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() {
        service = new PurchaseDocumentService(repository, lineRepository, sequenceRepository, warehouseRepository,
                stockService, companyProvider);
        companyId = UUID.randomUUID();
        supplierId = UUID.randomUUID();
        productId = UUID.randomUUID();
        warehouse = new Warehouse(companyId, "MAIN", "Principal");
        ReflectionTestUtils.setField(warehouse, "id", UUID.randomUUID());
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(warehouseRepository.findByIdAndCompanyId(warehouse.getId(), companyId))
                .thenReturn(Optional.of(warehouse));

        // Repositorios en memoria: el servicio lee lo que él mismo ha guardado.
        when(repository.saveAndFlush(any(PurchaseDocument.class))).thenAnswer(invocation -> {
            PurchaseDocument document = invocation.getArgument(0);
            ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
            documents.put(document.getId(), document);
            return document;
        });
        when(repository.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(documents.get(invocation.<UUID>getArgument(0))));
        when(repository.existsByCompanyIdAndSourceDocumentIdAndStatusNotAndIdNot(eq(companyId), any(), any(), any()))
                .thenAnswer(invocation -> documents.values().stream().anyMatch(document ->
                        invocation.getArgument(1).equals(document.getSourceDocumentId())
                                && document.getStatus() != invocation.getArgument(2)
                                && !document.getId().equals(invocation.getArgument(3))));
        when(lineRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<PurchaseDocumentLine> lines = invocation.getArgument(0);
            if (!lines.isEmpty()) linesByDocument.put(lines.getFirst().getDocumentId(), new ArrayList<>(lines));
            return lines;
        });
        when(lineRepository.findAllByCompanyIdAndDocumentIdOrderByLineSequenceAsc(eq(companyId), any()))
                .thenAnswer(invocation -> linesByDocument.getOrDefault(invocation.<UUID>getArgument(1), List.of()));
        doAnswer(invocation -> linesByDocument.remove(invocation.<UUID>getArgument(1)))
                .when(lineRepository).deleteAllByCompanyIdAndDocumentId(eq(companyId), any());
        when(sequenceRepository.findForUpdate(eq(companyId), any(), eq(2026))).thenAnswer(invocation ->
                Optional.ofNullable(sequences.get(invocation.getArgument(1).toString())));
        when(sequenceRepository.save(any(PurchaseDocumentSequence.class))).thenAnswer(invocation -> {
            PurchaseDocumentSequence sequence = invocation.getArgument(0);
            sequences.put(sequence.getType().toString(), sequence);
            return sequence;
        });
    }

    @Test
    void createsADraftWithConsecutiveNumbersPerTypeAndCalculatedTotals() {
        PurchaseDocumentResponse first = service.create(request(PurchaseDocumentType.PURCHASE_ORDER, null, null));
        PurchaseDocumentResponse second = service.create(request(PurchaseDocumentType.PURCHASE_ORDER, null, null));
        PurchaseDocumentResponse receipt = service.create(request(PurchaseDocumentType.GOODS_RECEIPT, null, null));

        assertThat(first.number()).isEqualTo("PC-2026-000001");
        assertThat(second.number()).isEqualTo("PC-2026-000002");
        assertThat(receipt.number()).isEqualTo("AC-2026-000001");
        assertThat(first.status()).isEqualTo(PurchaseDocumentStatus.DRAFT);
        assertThat(first.currencyCode()).isEqualTo("EUR");
        assertThat(first.lines()).extracting(PurchaseDtos.PurchaseLineResponse::sequence).containsExactly(1, 2);
        // 2 × 27,95 = 55,90 al 21 % y un porte de 10,00 al 21 %: base 65,90, cuota 13,84.
        assertThat(first.netAmount()).isEqualByComparingTo("65.90");
        assertThat(first.taxAmount()).isEqualByComparingTo("13.84");
        assertThat(first.totalAmount()).isEqualByComparingTo("79.74");
    }

    @Test
    void confirmingAGoodsReceiptPostsOneStockEntryPerProductLineWithItsUnitCost() {
        PurchaseDocumentResponse draft = service.create(
                request(PurchaseDocumentType.GOODS_RECEIPT, warehouse.getId(), null));

        PurchaseDocumentResponse confirmed = service.confirm(draft.id());

        assertThat(confirmed.status()).isEqualTo(PurchaseDocumentStatus.CONFIRMED);
        assertThat(confirmed.stockPosted()).isTrue();
        ArgumentCaptor<StockPosting> posting = ArgumentCaptor.forClass(StockPosting.class);
        // La línea de porte no tiene producto y no entra en almacén.
        verify(stockService, times(1)).post(eq(companyId), posting.capture());
        assertThat(posting.getValue().type()).isEqualTo(StockMovementType.PURCHASE_RECEIPT);
        assertThat(posting.getValue().warehouseId()).isEqualTo(warehouse.getId());
        assertThat(posting.getValue().productId()).isEqualTo(productId);
        assertThat(posting.getValue().quantity()).isEqualByComparingTo("2");
        assertThat(posting.getValue().unitCost()).isEqualByComparingTo("27.95");
        assertThat(posting.getValue().costCurrencyCode()).isEqualTo("EUR");
        assertThat(posting.getValue().sourceType()).isEqualTo(StockSourceType.PURCHASE_DOCUMENT);
        assertThat(posting.getValue().sourceId()).isEqualTo(draft.id());
        assertThat(posting.getValue().sourceNumber()).isEqualTo("AC-2026-000001");
    }

    @Test
    void aGoodsReceiptCannotBeConfirmedWithoutWarehouseNorTwice() {
        PurchaseDocumentResponse withoutWarehouse = service.create(
                request(PurchaseDocumentType.GOODS_RECEIPT, null, null));
        assertThatThrownBy(() -> service.confirm(withoutWarehouse.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("almacén");
        verify(stockService, never()).post(any(), any());

        PurchaseDocumentResponse receipt = service.create(
                request(PurchaseDocumentType.GOODS_RECEIPT, warehouse.getId(), null));
        service.confirm(receipt.id());
        assertThatThrownBy(() -> service.confirm(receipt.id())).isInstanceOf(BusinessRuleException.class);
        verify(stockService, times(1)).post(any(), any());
    }

    @Test
    void aPurchaseOrderNeverMovesStock() {
        PurchaseDocumentResponse order = service.create(
                request(PurchaseDocumentType.PURCHASE_ORDER, warehouse.getId(), null));

        assertThat(service.confirm(order.id()).stockPosted()).isFalse();
        verify(stockService, never()).post(any(), any());
    }

    @Test
    void aSupplierInvoiceNeedsTheSupplierNumberAndCannotBeRegisteredTwice() {
        PurchaseDocumentResponse missingReference = service.create(
                request(PurchaseDocumentType.SUPPLIER_INVOICE, null, null));
        assertThatThrownBy(() -> service.confirm(missingReference.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("número de factura");

        PurchaseDocumentResponse duplicate = service.create(
                request(PurchaseDocumentType.SUPPLIER_INVOICE, null, "F-77"));
        when(repository.existsSupplierInvoice(eq(companyId), eq(supplierId), eq("F-77"), any(), eq(duplicate.id())))
                .thenReturn(true);
        assertThatThrownBy(() -> service.confirm(duplicate.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya está registrada");
    }

    @Test
    void aDirectInvoiceWithWarehouseReceivesTheGoodsButAnInvoiceOfAReceiptDoesNot() {
        PurchaseDocumentResponse direct = service.create(
                request(PurchaseDocumentType.SUPPLIER_INVOICE, warehouse.getId(), "F-1"));
        assertThat(service.confirm(direct.id()).stockPosted()).isTrue();
        verify(stockService, times(1)).post(any(), any());

        PurchaseDocumentResponse receipt = service.create(
                request(PurchaseDocumentType.GOODS_RECEIPT, warehouse.getId(), null));
        service.confirm(receipt.id());
        verify(stockService, times(2)).post(any(), any());
        PurchaseDocumentResponse invoice = service.convert(receipt.id(),
                new ConvertPurchaseDocumentRequest(PurchaseDocumentType.SUPPLIER_INVOICE, ISSUE_DATE));
        assertThat(invoice.stockReceivedUpstream()).isTrue();
        PurchaseDocument invoiceEntity = documents.get(invoice.id());
        invoiceEntity.updateHeader(invoiceEntity.getSupplierId(), invoiceEntity.getSupplierCodeSnapshot(),
                invoiceEntity.getSupplierNameSnapshot(), null, "F-2", ISSUE_DATE, null,
                invoiceEntity.getWarehouseId(), "EUR", null);

        assertThat(service.confirm(invoice.id()).stockPosted()).isFalse();
        verify(stockService, times(2)).post(any(), any());
    }

    @Test
    void convertingCopiesTheLinesMarksTheSourceAndFollowsTheAllowedChain() {
        PurchaseDocumentResponse order = service.create(
                request(PurchaseDocumentType.PURCHASE_ORDER, warehouse.getId(), null));
        assertThatThrownBy(() -> service.convert(order.id(),
                new ConvertPurchaseDocumentRequest(PurchaseDocumentType.GOODS_RECEIPT, ISSUE_DATE)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("confirmado");
        service.confirm(order.id());

        PurchaseDocumentResponse receipt = service.convert(order.id(),
                new ConvertPurchaseDocumentRequest(PurchaseDocumentType.GOODS_RECEIPT, ISSUE_DATE));

        assertThat(receipt.number()).isEqualTo("AC-2026-000001");
        assertThat(receipt.status()).isEqualTo(PurchaseDocumentStatus.DRAFT);
        assertThat(receipt.sourceDocumentId()).isEqualTo(order.id());
        assertThat(receipt.warehouseId()).isEqualTo(warehouse.getId());
        assertThat(receipt.stockReceivedUpstream()).isFalse();
        assertThat(receipt.lines()).hasSize(2);
        assertThat(receipt.totalAmount()).isEqualByComparingTo(order.totalAmount());
        assertThat(documents.get(order.id()).getStatus()).isEqualTo(PurchaseDocumentStatus.CONVERTED);
        assertThatThrownBy(() -> service.convert(receipt.id(),
                new ConvertPurchaseDocumentRequest(PurchaseDocumentType.PURCHASE_ORDER, ISSUE_DATE)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancellingAReceiptReversesItsStockAndReopensTheOrderItCameFrom() {
        PurchaseDocumentResponse order = service.create(
                request(PurchaseDocumentType.PURCHASE_ORDER, warehouse.getId(), null));
        service.confirm(order.id());
        PurchaseDocumentResponse receipt = service.convert(order.id(),
                new ConvertPurchaseDocumentRequest(PurchaseDocumentType.GOODS_RECEIPT, ISSUE_DATE));
        service.confirm(receipt.id());
        assertThatThrownBy(() -> service.cancel(order.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya se convirtió");

        PurchaseDocumentResponse cancelled = service.cancel(receipt.id());

        assertThat(cancelled.status()).isEqualTo(PurchaseDocumentStatus.CANCELLED);
        assertThat(cancelled.stockPosted()).isFalse();
        ArgumentCaptor<StockPosting> posting = ArgumentCaptor.forClass(StockPosting.class);
        verify(stockService, times(2)).post(eq(companyId), posting.capture());
        assertThat(posting.getAllValues()).extracting(StockPosting::type)
                .containsExactly(StockMovementType.PURCHASE_RECEIPT, StockMovementType.PURCHASE_REVERSAL);
        assertThat(posting.getAllValues().get(1).quantity()).isEqualByComparingTo("2");
        assertThat(documents.get(order.id()).getStatus()).isEqualTo(PurchaseDocumentStatus.CONFIRMED);
        assertThatThrownBy(() -> service.cancel(receipt.id())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void onlyDraftsCanBeEditedOrDeletedAndDeletingAConvertedDraftReopensItsSource() {
        PurchaseDocumentResponse order = service.create(
                request(PurchaseDocumentType.PURCHASE_ORDER, warehouse.getId(), null));
        service.confirm(order.id());
        assertThatThrownBy(() -> service.update(order.id(),
                request(PurchaseDocumentType.PURCHASE_ORDER, warehouse.getId(), null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.delete(order.id())).isInstanceOf(BusinessRuleException.class);

        PurchaseDocumentResponse receipt = service.convert(order.id(),
                new ConvertPurchaseDocumentRequest(PurchaseDocumentType.GOODS_RECEIPT, ISSUE_DATE));
        doAnswer(invocation -> documents.remove(invocation.<PurchaseDocument>getArgument(0).getId()))
                .when(repository).delete(any(PurchaseDocument.class));
        service.delete(receipt.id());

        assertThat(documents).doesNotContainKey(receipt.id());
        assertThat(documents.get(order.id()).getStatus()).isEqualTo(PurchaseDocumentStatus.CONFIRMED);
    }

    @Test
    void validatesDatesProductSnapshotsAndImmutableTypeAndSupplier() {
        PurchaseDocumentRequest lateExpected = new PurchaseDocumentRequest(PurchaseDocumentType.PURCHASE_ORDER,
                supplierId, "PR-1", "Proveedor", null, null, ISSUE_DATE, ISSUE_DATE.minusDays(1), null, "EUR",
                null, lines());
        assertThatThrownBy(() -> service.create(lateExpected)).isInstanceOf(BusinessRuleException.class);

        PurchaseDocumentRequest productWithoutCode = new PurchaseDocumentRequest(
                PurchaseDocumentType.PURCHASE_ORDER, supplierId, "PR-1", "Proveedor", null, null, ISSUE_DATE, null,
                null, "EUR", null, List.of(new PurchaseLineRequest(productId, " ", "Producto", "UNIT",
                BigDecimal.ONE, BigDecimal.ONE, null, null)));
        assertThatThrownBy(() -> service.create(productWithoutCode)).isInstanceOf(BusinessRuleException.class);

        PurchaseDocumentResponse order = service.create(request(PurchaseDocumentType.PURCHASE_ORDER, null, null));
        assertThatThrownBy(() -> service.update(order.id(),
                request(PurchaseDocumentType.GOODS_RECEIPT, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tipo");
    }

    private PurchaseDocumentRequest request(PurchaseDocumentType type, UUID warehouseId, String reference) {
        return new PurchaseDocumentRequest(type, supplierId, "PR-1", "Proveedor Demo", "B12345678", reference,
                ISSUE_DATE, null, warehouseId, "eur", null, lines());
    }

    private List<PurchaseLineRequest> lines() {
        return List.of(
                new PurchaseLineRequest(productId, "P-1", "Producto", "unit", new BigDecimal("2"),
                        new BigDecimal("27.95"), BigDecimal.ZERO, new BigDecimal("21")),
                new PurchaseLineRequest(null, null, "Portes", "UNIT", BigDecimal.ONE, new BigDecimal("10"),
                        null, new BigDecimal("21")));
    }
}
