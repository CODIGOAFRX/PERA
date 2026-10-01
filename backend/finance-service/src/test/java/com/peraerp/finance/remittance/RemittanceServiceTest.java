package com.peraerp.finance.remittance;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.receivable.CollectionMethod;
import com.peraerp.finance.receivable.CollectionNumbering;
import com.peraerp.finance.receivable.CollectionSequence;
import com.peraerp.finance.receivable.CollectionSequenceRepository;
import com.peraerp.finance.receivable.DocumentDueDate;
import com.peraerp.finance.receivable.DocumentDueDateRepository;
import com.peraerp.finance.receivable.DueDateStatus;
import com.peraerp.finance.receivable.InvoiceCollectionTracker;
import com.peraerp.finance.receivable.InvoicePaymentNotifier;
import com.peraerp.finance.receivable.InvoicePaymentStatus;
import com.peraerp.finance.receivable.Receipt;
import com.peraerp.finance.receivable.ReceiptRepository;
import com.peraerp.finance.receivable.ReceiptStatus;
import com.peraerp.finance.remittance.RemittanceDtos.RemittanceRequest;
import com.peraerp.finance.remittance.RemittanceDtos.RemittanceResponse;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RemittanceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Mock RemittanceRepository repository;
    @Mock ReceiptRepository receipts;
    @Mock DocumentDueDateRepository dueDates;
    @Mock CollectionSequenceRepository sequences;
    @Mock InvoicePaymentNotifier notifier;
    @Mock CurrentCompanyProvider companyProvider;

    private final Map<UUID, Remittance> remittances = new HashMap<>();
    private final List<Receipt> stored = new ArrayList<>();
    private final Map<UUID, DocumentDueDate> dueDateById = new HashMap<>();
    private RemittanceService service;
    private UUID companyId;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        service = new RemittanceService(repository, receipts, new CollectionNumbering(sequences),
                new InvoiceCollectionTracker(dueDates, receipts, notifier), companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(notifier.notify(any(), any())).thenReturn(true);
        when(sequences.findForUpdate(eq(companyId), eq(CollectionSequence.REMITTANCE), anyInt()))
                .thenReturn(Optional.of(new CollectionSequence(companyId, CollectionSequence.REMITTANCE, 2026)));
        when(repository.saveAndFlush(any(Remittance.class))).thenAnswer(invocation -> {
            Remittance remittance = invocation.getArgument(0);
            ReflectionTestUtils.setField(remittance, "id", UUID.randomUUID());
            remittances.put(remittance.getId(), remittance);
            return remittance;
        });
        when(repository.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(remittances.get(invocation.<UUID>getArgument(0))));
        when(receipts.findAllByCompanyIdAndIdIn(eq(companyId), any())).thenAnswer(invocation -> stored.stream()
                .filter(receipt -> invocation.<Collection<UUID>>getArgument(1).contains(receipt.getId())).toList());
        when(receipts.findAllByCompanyIdAndRemittanceIdOrderByDueDateAscReceiptNumberAsc(eq(companyId), any()))
                .thenAnswer(invocation -> stored.stream()
                        .filter(receipt -> invocation.getArgument(1).equals(receipt.getRemittanceId()))
                        .sorted(Comparator.comparing(Receipt::getReceiptNumber)).toList());
        when(receipts.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumberAsc(companyId, documentId))
                .thenAnswer(invocation -> List.copyOf(stored));
        when(dueDates.findByIdAndCompanyId(any(), eq(companyId)))
                .thenAnswer(invocation -> Optional.ofNullable(dueDateById.get(invocation.<UUID>getArgument(0))));
    }

    @Test
    void createsADraftWithTheSelectedReceiptsAndTheirTotal() {
        Receipt first = receipt(1, "60.00", "EUR");
        Receipt second = receipt(2, "40.00", "EUR");

        RemittanceResponse draft = service.create(request(first, second));

        assertThat(draft.remittanceNumber()).isEqualTo("REM-2026-0001");
        assertThat(draft.status()).isEqualTo(RemittanceStatus.DRAFT);
        assertThat(draft.totalAmount()).isEqualByComparingTo("100.00");
        assertThat(draft.currencyCode()).isEqualTo("EUR");
        assertThat(draft.receipts()).hasSize(2);
        // En borrador el recibo sigue pendiente, pero ya no está disponible para otra remesa ni para cobrarse.
        assertThat(first.getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(first.getRemittanceId()).isEqualTo(draft.id());
        assertThatThrownBy(() -> service.create(request(first))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> first.collect(DAY, CollectionMethod.CASH, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMixedCurrenciesAndUnknownReceipts() {
        Receipt euros = receipt(1, "60.00", "EUR");
        Receipt dollars = receipt(2, "40.00", "USD");

        assertThatThrownBy(() -> service.create(request(euros, dollars))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("misma moneda");
        assertThatThrownBy(() -> service.create(new RemittanceRequest("ES00", DAY, null,
                List.of(euros.getId(), UUID.randomUUID())))).isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void sendingAndSettlingCollectsTheReceiptsAndMarksTheInvoicePaid() {
        Receipt first = receipt(1, "60.00", "EUR");
        Receipt second = receipt(2, "40.00", "EUR");
        RemittanceResponse draft = service.create(request(first, second));
        assertThatThrownBy(() -> service.settle(draft.id(), DAY)).isInstanceOf(BusinessRuleException.class);

        RemittanceResponse sent = service.send(draft.id(), DAY.plusDays(1));
        assertThat(sent.status()).isEqualTo(RemittanceStatus.SENT);
        assertThat(first.getStatus()).isEqualTo(ReceiptStatus.REMITTED);
        assertThatThrownBy(() -> service.settle(draft.id(), DAY)).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anterior");

        RemittanceResponse settled = service.settle(draft.id(), DAY.plusDays(3));

        assertThat(settled.status()).isEqualTo(RemittanceStatus.SETTLED);
        assertThat(settled.invoiceUpdated()).isTrue();
        assertThat(first.getStatus()).isEqualTo(ReceiptStatus.COLLECTED);
        assertThat(first.getCollectionMethod()).isEqualTo(CollectionMethod.DIRECT_DEBIT);
        assertThat(first.getCollectionDate()).isEqualTo(DAY.plusDays(3));
        assertThat(dueDateById.get(first.getDueDateId()).getStatus()).isEqualTo(DueDateStatus.PAID);
        verify(notifier).notify(documentId, InvoicePaymentStatus.PAID);
    }

    @Test
    void aReceiptReturnedBeforeTheBankPaysIsLeftOutOfTheSettlement() {
        Receipt first = receipt(1, "60.00", "EUR");
        Receipt second = receipt(2, "40.00", "EUR");
        RemittanceResponse draft = service.create(request(first, second));
        service.send(draft.id(), DAY);
        second.markReturned(DAY.plusDays(1), "Cuenta cancelada");

        RemittanceResponse settled = service.settle(draft.id(), DAY.plusDays(2));

        assertThat(settled.status()).isEqualTo(RemittanceStatus.PARTIALLY_RETURNED);
        assertThat(first.getStatus()).isEqualTo(ReceiptStatus.COLLECTED);
        assertThat(second.getStatus()).isEqualTo(ReceiptStatus.RETURNED);
        verify(notifier).notify(documentId, InvoicePaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void editingADraftReplacesItsReceiptsAndFreesTheOnesTakenOut() {
        Receipt first = receipt(1, "60.00", "EUR");
        Receipt second = receipt(2, "40.00", "EUR");
        Receipt third = receipt(3, "25.00", "EUR");
        RemittanceResponse draft = service.create(request(first, second));

        RemittanceResponse updated = service.update(draft.id(), new RemittanceRequest(" ES11 ", DAY, "Otra cuenta",
                List.of(second.getId(), third.getId())));

        assertThat(updated.bankAccount()).isEqualTo("ES11");
        assertThat(updated.totalAmount()).isEqualByComparingTo("65.00");
        assertThat(updated.receipts()).extracting(receipt -> receipt.installment()).containsExactly(2, 3);
        assertThat(first.getRemittanceId()).isNull();
        service.send(draft.id(), DAY);
        assertThatThrownBy(() -> service.update(draft.id(), request(second))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancellingASentRemittanceReturnsItsReceiptsToPending() {
        Receipt first = receipt(1, "60.00", "EUR");
        RemittanceResponse draft = service.create(request(first));
        service.send(draft.id(), DAY);

        RemittanceResponse cancelled = service.cancel(draft.id());

        assertThat(cancelled.status()).isEqualTo(RemittanceStatus.CANCELLED);
        assertThat(first.getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(first.getRemittanceId()).isNull();
        assertThatThrownBy(() -> service.cancel(draft.id())).isInstanceOf(BusinessRuleException.class);
    }

    private RemittanceRequest request(Receipt... selected) {
        return new RemittanceRequest("ES00 0000 0000", DAY, null,
                java.util.Arrays.stream(selected).map(Receipt::getId).toList());
    }

    private Receipt receipt(int installment, String amount, String currency) {
        DocumentDueDate dueDate = new DocumentDueDate(companyId, documentId, installment, DAY.plusDays(30),
                new BigDecimal(amount));
        ReflectionTestUtils.setField(dueDate, "id", UUID.randomUUID());
        dueDateById.put(dueDate.getId(), dueDate);
        Receipt receipt = new Receipt(companyId, "REC-2026-%06d".formatted(installment), dueDate, UUID.randomUUID(),
                "C001", "Cliente Demo", "FAC-2026-000001", currency);
        ReflectionTestUtils.setField(receipt, "id", UUID.randomUUID());
        stored.add(receipt);
        return receipt;
    }
}
