package com.peraerp.finance.accounting;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingServiceTest {
    @Mock AccountingAccountRepository accounts;
    @Mock AccountingInboxRepository inbox;
    @Mock AccountingJournalEntryRepository entries;
    @Mock SalesInvoiceClient sales;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private AccountingService service;

    @BeforeEach
    void setUp() {
        service = new AccountingService(accounts, inbox, entries, sales, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void synchronizesOnlyTheActiveCompanyBeforeCountingPendingWork() {
        SalesInvoiceSnapshot invoice = snapshot(UUID.randomUUID(), "FAC-2026-000001", "CONFIRMED");
        when(sales.findInvoices(companyId)).thenReturn(List.of(invoice));
        when(inbox.findByCompanyIdAndSourceTypeAndSourceId(companyId, "INVOICE", invoice.id()))
                .thenReturn(Optional.empty());
        when(inbox.countByCompanyIdAndInboxStatus(companyId, AccountingInboxStatus.PENDING)).thenReturn(1L);

        AccountingCountResponse response = service.pendingCount();

        assertThat(response.pending()).isOne();
        verify(sales).findInvoices(companyId);
        verify(inbox).save(any(AccountingInboxItem.class));
    }

    @Test
    void postsABalancedEntryAndMarksItsInvoiceAsCompleted() {
        UUID itemId = UUID.randomUUID();
        AccountingInboxItem item = new AccountingInboxItem(companyId,
                snapshot(UUID.randomUUID(), "FAC-2026-000002", "CONFIRMED"));
        ReflectionTestUtils.setField(item, "id", itemId);
        AccountingAccount customer = account("430000", "Clientes", AccountKind.ASSET);
        AccountingAccount salesAccount = account("700000", "Ventas", AccountKind.INCOME);
        when(inbox.findByIdAndCompanyIdAndInboxStatus(itemId, companyId, AccountingInboxStatus.PENDING))
                .thenReturn(Optional.of(item));
        when(accounts.findByIdAndCompanyIdAndActiveTrue(customer.getId(), companyId)).thenReturn(Optional.of(customer));
        when(accounts.findByIdAndCompanyIdAndActiveTrue(salesAccount.getId(), companyId)).thenReturn(Optional.of(salesAccount));
        when(entries.save(any(AccountingJournalEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));
        AccountingEntryRequest request = new AccountingEntryRequest(LocalDate.of(2026, 8, 25), "Venta",
                List.of(new AccountingLineRequest(customer.getId(), null, new BigDecimal("121"), BigDecimal.ZERO),
                        new AccountingLineRequest(salesAccount.getId(), null, BigDecimal.ZERO, new BigDecimal("121"))));

        AccountingEntryResponse response = service.postInboxItem(itemId, request);

        assertThat(response.total()).isEqualByComparingTo("121");
        assertThat(response.lines()).hasSize(2);
        assertThat(item.getInboxStatus()).isEqualTo(AccountingInboxStatus.POSTED);
    }

    @Test
    void refusesAnEntryWhoseDebitAndCreditDoNotBalance() {
        AccountingAccount cash = account("570000", "Caja", AccountKind.ASSET);
        AccountingAccount salesAccount = account("700000", "Ventas", AccountKind.INCOME);
        when(accounts.findByIdAndCompanyIdAndActiveTrue(cash.getId(), companyId)).thenReturn(Optional.of(cash));
        when(accounts.findByIdAndCompanyIdAndActiveTrue(salesAccount.getId(), companyId)).thenReturn(Optional.of(salesAccount));
        AccountingEntryRequest request = new AccountingEntryRequest(LocalDate.now(), "Descuadre",
                List.of(new AccountingLineRequest(cash.getId(), null, new BigDecimal("100"), BigDecimal.ZERO),
                        new AccountingLineRequest(salesAccount.getId(), null, BigDecimal.ZERO, new BigDecimal("99"))));

        assertThatThrownBy(() -> service.postManual(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no cuadra");
    }

    @Test
    void refusesToPostADraftInvoice() {
        UUID itemId = UUID.randomUUID();
        AccountingInboxItem item = new AccountingInboxItem(companyId,
                snapshot(UUID.randomUUID(), "FAC-BORRADOR", "DRAFT"));
        when(inbox.findByIdAndCompanyIdAndInboxStatus(itemId, companyId, AccountingInboxStatus.PENDING))
                .thenReturn(Optional.of(item));
        AccountingEntryRequest request = new AccountingEntryRequest(LocalDate.now(), "Borrador", List.of(
                new AccountingLineRequest(UUID.randomUUID(), null, BigDecimal.ONE, BigDecimal.ZERO),
                new AccountingLineRequest(UUID.randomUUID(), null, BigDecimal.ZERO, BigDecimal.ONE)));

        assertThatThrownBy(() -> service.postInboxItem(itemId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("borrador");
    }

    private AccountingAccount account(String code, String name, AccountKind kind) {
        AccountingAccount account = new AccountingAccount(companyId, code, name, kind, true);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }

    private SalesInvoiceSnapshot snapshot(UUID id, String number, String status) {
        return new SalesInvoiceSnapshot(id, number, "INVOICE", status, LocalDate.of(2026, 8, 25),
                "C001", "Cliente Demo", "EUR", new BigDecimal("100"), new BigDecimal("21"),
                new BigDecimal("121"));
    }
}
