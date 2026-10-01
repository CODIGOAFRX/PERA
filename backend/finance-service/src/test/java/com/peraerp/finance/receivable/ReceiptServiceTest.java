package com.peraerp.finance.receivable;

import com.peraerp.finance.cash.CashService;
import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.finance.receivable.ReceiptDtos.CollectReceiptRequest;
import com.peraerp.finance.receivable.ReceiptDtos.IssueReceiptsRequest;
import com.peraerp.finance.receivable.ReceiptDtos.ReceiptOperationResponse;
import com.peraerp.finance.receivable.ReceiptDtos.ReceiptResponse;
import com.peraerp.finance.receivable.ReceiptDtos.ReopenReceiptRequest;
import com.peraerp.finance.receivable.ReceiptDtos.ReturnReceiptRequest;
import com.peraerp.finance.remittance.Remittance;
import com.peraerp.finance.remittance.RemittanceRepository;
import com.peraerp.finance.remittance.RemittanceStatus;
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
import java.util.List;
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
class ReceiptServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Mock ReceiptRepository repository;
    @Mock DocumentDueDateRepository dueDates;
    @Mock RemittanceRepository remittances;
    @Mock CollectionSequenceRepository sequences;
    @Mock InvoicePaymentNotifier notifier;
    @Mock CashService cashService;
    @Mock CurrentCompanyProvider companyProvider;

    private final List<Receipt> stored = new ArrayList<>();
    private ReceiptService service;
    private UUID companyId;
    private UUID documentId;
    private UUID customerId;
    private DocumentDueDate first;
    private DocumentDueDate second;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        first = dueDate(1, "60.00", TODAY.plusDays(30));
        second = dueDate(2, "40.00", TODAY.plusDays(60));
        InvoiceCollectionTracker tracker = new InvoiceCollectionTracker(dueDates, repository, notifier);
        service = new ReceiptService(repository, dueDates, remittances, new CollectionNumbering(sequences), tracker,
                cashService, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(dueDates.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumber(companyId, documentId))
                .thenReturn(List.of(first, second));
        when(dueDates.findByIdAndCompanyId(first.getId(), companyId)).thenReturn(Optional.of(first));
        when(dueDates.findByIdAndCompanyId(second.getId(), companyId)).thenReturn(Optional.of(second));
        CollectionSequence sequence = new CollectionSequence(companyId, CollectionSequence.RECEIPT, TODAY.getYear());
        when(sequences.findForUpdate(eq(companyId), eq(CollectionSequence.RECEIPT), anyInt()))
                .thenReturn(Optional.of(sequence));
        when(notifier.notify(any(), any())).thenReturn(true);

        // Repositorio en memoria.
        when(repository.save(any(Receipt.class))).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(0);
            ReflectionTestUtils.setField(receipt, "id", UUID.randomUUID());
            stored.add(receipt);
            return receipt;
        });
        when(repository.findByIdAndCompanyId(any(), eq(companyId))).thenAnswer(invocation -> stored.stream()
                .filter(receipt -> receipt.getId().equals(invocation.getArgument(0))).findFirst());
        when(repository.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumberAsc(companyId, documentId))
                .thenAnswer(invocation -> List.copyOf(stored));
        when(repository.existsByCompanyIdAndDueDateIdAndStatusNot(eq(companyId), any(), eq(ReceiptStatus.CANCELLED)))
                .thenAnswer(invocation -> stored.stream().anyMatch(receipt ->
                        invocation.getArgument(1).equals(receipt.getDueDateId())
                                && receipt.getStatus() != ReceiptStatus.CANCELLED));
    }

    @Test
    void issuesOneNumberedReceiptPerDueDateAndNeverTwice() {
        List<ReceiptResponse> issued = service.issue(issueRequest());

        assertThat(issued).extracting(ReceiptResponse::receiptNumber)
                .containsExactly("REC-%d-000001".formatted(TODAY.getYear()), "REC-%d-000002".formatted(TODAY.getYear()));
        assertThat(issued).extracting(ReceiptResponse::amount).containsExactly(new BigDecimal("60.00"),
                new BigDecimal("40.00"));
        assertThat(issued.getFirst().documentNumber()).isEqualTo("FAC-2026-000001");
        assertThat(issued.getFirst().customerName()).isEqualTo("Cliente Demo");
        assertThat(issued.getFirst().currencyCode()).isEqualTo("EUR");
        assertThat(issued.getFirst().status()).isEqualTo(ReceiptStatus.PENDING);

        assertThatThrownBy(() -> service.issue(issueRequest())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ya tienen recibo");
    }

    @Test
    void requiresTheDueDatesToExistBeforeIssuing() {
        when(dueDates.findAllByCompanyIdAndDocumentIdOrderByInstallmentNumber(companyId, documentId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.issue(issueRequest())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vencimientos");
        verify(repository, never()).save(any());
    }

    @Test
    void collectingSettlesTheDueDateAndTellsSalesHowMuchOfTheInvoiceIsPaid() {
        List<ReceiptResponse> issued = service.issue(issueRequest());

        ReceiptOperationResponse partial = service.collect(issued.get(0).id(),
                new CollectReceiptRequest(TODAY, CollectionMethod.BANK_TRANSFER, null, " Transferencia "));

        assertThat(partial.invoiceUpdated()).isTrue();
        assertThat(partial.receipts().getFirst().status()).isEqualTo(ReceiptStatus.COLLECTED);
        assertThat(partial.receipts().getFirst().notes()).isEqualTo("Transferencia");
        assertThat(first.getStatus()).isEqualTo(DueDateStatus.PAID);
        assertThat(first.getPaidAmount()).isEqualByComparingTo("60.00");
        assertThat(second.getStatus()).isEqualTo(DueDateStatus.PENDING);
        verify(notifier).notify(documentId, InvoicePaymentStatus.PARTIALLY_PAID);

        service.collect(issued.get(1).id(), new CollectReceiptRequest(TODAY, CollectionMethod.CARD, null, null));
        verify(notifier).notify(documentId, InvoicePaymentStatus.PAID);
        assertThatThrownBy(() -> service.collect(issued.get(1).id(),
                new CollectReceiptRequest(TODAY, CollectionMethod.CARD, null, null)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aCashCollectionIsRecordedInTheChosenCashSessionAndOnlyCashCanBe() {
        List<ReceiptResponse> issued = service.issue(issueRequest());
        UUID sessionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.collect(issued.get(0).id(),
                new CollectReceiptRequest(TODAY, CollectionMethod.CARD, sessionId, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.collect(issued.get(0).id(),
                new CollectReceiptRequest(TODAY.plusDays(5), CollectionMethod.CASH, sessionId, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("futura");

        service.collect(issued.get(0).id(), new CollectReceiptRequest(TODAY, CollectionMethod.CASH, sessionId, null));

        verify(cashService).recordReceiptCollection(companyId, sessionId, stored.getFirst());
    }

    @Test
    void aReturnReopensTheDueDateAndTheInvoiceAndTheReceiptCanCirculateAgain() {
        List<ReceiptResponse> issued = service.issue(issueRequest());
        UUID receiptId = issued.get(0).id();
        service.collect(receiptId, new CollectReceiptRequest(TODAY, CollectionMethod.BANK_TRANSFER, null, null));

        ReceiptOperationResponse returned = service.returnReceipt(receiptId,
                new ReturnReceiptRequest(TODAY, " Sin fondos "));

        assertThat(returned.receipts().getFirst().status()).isEqualTo(ReceiptStatus.RETURNED);
        assertThat(returned.receipts().getFirst().returnReason()).isEqualTo("Sin fondos");
        assertThat(first.getStatus()).isEqualTo(DueDateStatus.PENDING);
        assertThat(first.getPaidAmount()).isEqualByComparingTo("0");
        verify(notifier).notify(documentId, InvoicePaymentStatus.PENDING);

        LocalDate newDueDate = TODAY.plusDays(15);
        ReceiptOperationResponse reopened = service.reopen(receiptId, new ReopenReceiptRequest(newDueDate));
        assertThat(reopened.receipts().getFirst().status()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(reopened.receipts().getFirst().dueDate()).isEqualTo(newDueDate);
        assertThat(reopened.receipts().getFirst().collectionDate()).isNull();
    }

    @Test
    void aReturnAfterTheBankSettledTheRemittanceMarksItAsPartiallyReturned() {
        List<ReceiptResponse> issued = service.issue(issueRequest());
        Receipt receipt = stored.getFirst();
        Remittance remittance = new Remittance(companyId, "REM-2026-0001", "ES00", "EUR", TODAY, null);
        ReflectionTestUtils.setField(remittance, "id", UUID.randomUUID());
        receipt.assignTo(remittance.getId());
        remittance.updateDraft("ES00", null, receipt.getAmount());
        remittance.send(TODAY);
        receipt.markRemitted();
        remittance.settle(TODAY, false);
        receipt.settle(TODAY);
        when(remittances.findByIdAndCompanyId(remittance.getId(), companyId)).thenReturn(Optional.of(remittance));

        service.returnReceipt(issued.get(0).id(), new ReturnReceiptRequest(TODAY, "Devuelto por el banco"));

        assertThat(remittance.getStatus()).isEqualTo(RemittanceStatus.PARTIALLY_RETURNED);
        assertThat(receipt.getRemittanceId()).isEqualTo(remittance.getId());
    }

    @Test
    void theCollectionStandsEvenWhenSalesCannotBeUpdated() {
        List<ReceiptResponse> issued = service.issue(issueRequest());
        when(notifier.notify(any(), any())).thenReturn(false);

        ReceiptOperationResponse response = service.collect(issued.get(0).id(),
                new CollectReceiptRequest(TODAY, CollectionMethod.BANK_TRANSFER, null, null));

        assertThat(response.invoiceUpdated()).isFalse();
        assertThat(response.receipts().getFirst().status()).isEqualTo(ReceiptStatus.COLLECTED);
    }

    @Test
    void aCancelledReceiptFreesItsDueDateForANewOne() {
        List<ReceiptResponse> issued = service.issue(issueRequest());

        service.cancel(issued.get(0).id());
        List<ReceiptResponse> reissued = service.issue(issueRequest());

        assertThat(reissued).singleElement().satisfies(receipt -> {
            assertThat(receipt.installment()).isEqualTo(1);
            assertThat(receipt.receiptNumber()).endsWith("000003");
        });
        assertThatThrownBy(() -> service.cancel(issued.get(0).id())).isInstanceOf(BusinessRuleException.class);
    }

    private IssueReceiptsRequest issueRequest() {
        return new IssueReceiptsRequest(documentId, "FAC-2026-000001", customerId, "C001", "Cliente Demo", "eur");
    }

    private DocumentDueDate dueDate(int installment, String amount, LocalDate date) {
        DocumentDueDate dueDate = new DocumentDueDate(companyId, documentId, installment, date, new BigDecimal(amount));
        ReflectionTestUtils.setField(dueDate, "id", UUID.randomUUID());
        return dueDate;
    }
}
