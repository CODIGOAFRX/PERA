package com.peraerp.finance.accounting;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountingService {
    private static final List<DefaultAccount> DEFAULT_ACCOUNTS = List.of(
            new DefaultAccount("400000", "Proveedores", AccountKind.LIABILITY),
            new DefaultAccount("430000", "Clientes", AccountKind.ASSET),
            new DefaultAccount("477000", "IVA repercutido", AccountKind.LIABILITY),
            new DefaultAccount("555000", "Partidas pendientes", AccountKind.LIABILITY),
            new DefaultAccount("570000", "Caja", AccountKind.ASSET),
            new DefaultAccount("572000", "Bancos", AccountKind.ASSET),
            new DefaultAccount("600000", "Compras", AccountKind.EXPENSE),
            new DefaultAccount("700000", "Ventas de mercaderías", AccountKind.INCOME),
            new DefaultAccount("705000", "Prestaciones de servicios", AccountKind.INCOME)
    );
    private static final Map<String, String> SEARCH_ALIASES = Map.ofEntries(
            Map.entry("efectivo", "caja"), Map.entry("cash", "caja"), Map.entry("banco", "bancos"),
            Map.entry("bank", "bancos"), Map.entry("cliente", "clientes"), Map.entry("customer", "clientes"),
            Map.entry("venta", "ventas"), Map.entry("sale", "ventas"), Map.entry("compra", "compras"),
            Map.entry("purchase", "compras"), Map.entry("proveedor", "proveedores"), Map.entry("supplier", "proveedores"));

    private final AccountingAccountRepository accounts;
    private final AccountingInboxRepository inbox;
    private final AccountingJournalEntryRepository entries;
    private final SalesInvoiceClient sales;
    private final CurrentCompanyProvider companyProvider;

    public AccountingService(AccountingAccountRepository accounts, AccountingInboxRepository inbox,
                             AccountingJournalEntryRepository entries, SalesInvoiceClient sales,
                             CurrentCompanyProvider companyProvider) {
        this.accounts = accounts;
        this.inbox = inbox;
        this.entries = entries;
        this.sales = sales;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public List<AccountingAccountResponse> findAccounts(String query) {
        UUID companyId = companyProvider.requireCompanyId();
        ensureDefaultAccounts(companyId);
        String normalized = normalize(query);
        normalized = SEARCH_ALIASES.getOrDefault(normalized, normalized);
        String filter = normalized;
        return accounts.findAllByCompanyIdAndActiveTrueOrderByCode(companyId).stream()
                .filter(account -> filter.isBlank() || normalize(account.getCode()).contains(filter)
                        || normalize(account.getName()).contains(filter))
                .map(AccountingAccountResponse::from).toList();
    }

    @Transactional
    public AccountingCountResponse pendingCount() {
        UUID companyId = companyProvider.requireCompanyId();
        synchronizeInvoices(companyId);
        return new AccountingCountResponse(inbox.countByCompanyIdAndInboxStatus(companyId, AccountingInboxStatus.PENDING));
    }

    @Transactional
    public List<AccountingInboxResponse> findPending() {
        UUID companyId = companyProvider.requireCompanyId();
        ensureDefaultAccounts(companyId);
        synchronizeInvoices(companyId);
        Map<String, AccountingAccount> defaults = defaultAccountMap(companyId);
        return inbox.findAllByCompanyIdAndInboxStatusOrderBySourceDateDescCreatedAtDesc(
                companyId, AccountingInboxStatus.PENDING).stream()
                .map(item -> toInboxResponse(item, defaults)).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountingEntryResponse> findEntries() {
        return entries.findTop100ByCompanyIdOrderByEntryDateDescCreatedAtDesc(companyProvider.requireCompanyId())
                .stream().map(AccountingEntryResponse::from).toList();
    }

    @Transactional
    public AccountingEntryResponse postInboxItem(UUID itemId, AccountingEntryRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        AccountingInboxItem item = inbox.findByIdAndCompanyIdAndInboxStatus(itemId, companyId,
                        AccountingInboxStatus.PENDING)
                .orElseThrow(() -> new ResourceNotFoundException("Pendiente contable", itemId));
        if (!item.isIssued()) {
            throw new BusinessRuleException("La factura todavía está en borrador. Confírmala en Ventas antes de contabilizarla.");
        }
        AccountingJournalEntry entry = createEntry(companyId, request, item.getSourceType(), item.getSourceId(),
                item.getSourceNumber());
        item.markPosted(entry);
        return AccountingEntryResponse.from(entry);
    }

    @Transactional
    public AccountingEntryResponse postManual(AccountingEntryRequest request) {
        return AccountingEntryResponse.from(createEntry(companyProvider.requireCompanyId(), request,
                "MANUAL", null, null));
    }

    private AccountingJournalEntry createEntry(UUID companyId, AccountingEntryRequest request, String sourceType,
                                               UUID sourceId, String sourceNumber) {
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        List<ResolvedLine> resolved = new ArrayList<>();
        for (AccountingLineRequest line : request.lines()) {
            BigDecimal debit = money(line.debit());
            BigDecimal credit = money(line.credit());
            if ((debit.signum() > 0) == (credit.signum() > 0)) {
                throw new BusinessRuleException("Cada línea debe tener importe solo en Debe o solo en Haber.");
            }
            AccountingAccount account = accounts.findByIdAndCompanyIdAndActiveTrue(line.accountId(), companyId)
                    .orElseThrow(() -> new BusinessRuleException("Una cuenta contable no existe en la empresa activa."));
            debitTotal = debitTotal.add(debit);
            creditTotal = creditTotal.add(credit);
            resolved.add(new ResolvedLine(account, line.description(), debit, credit));
        }
        if (debitTotal.signum() <= 0 || debitTotal.compareTo(creditTotal) != 0) {
            throw new BusinessRuleException("El asiento no cuadra: el total del Debe debe ser igual al total del Haber.");
        }
        AccountingJournalEntry entry = new AccountingJournalEntry(companyId, request.entryDate(),
                request.description().trim(), sourceType, sourceId, sourceNumber);
        resolved.forEach(line -> entry.addLine(line.account(), clean(line.description()), line.debit(), line.credit()));
        return entries.save(entry);
    }

    private void synchronizeInvoices(UUID companyId) {
        for (SalesInvoiceSnapshot snapshot : sales.findInvoices(companyId)) {
            AccountingInboxItem item = inbox.findByCompanyIdAndSourceTypeAndSourceId(companyId, snapshot.type(), snapshot.id())
                    .orElseGet(() -> new AccountingInboxItem(companyId, snapshot));
            item.update(snapshot);
            inbox.save(item);
        }
    }

    private void ensureDefaultAccounts(UUID companyId) {
        List<AccountingAccount> missing = DEFAULT_ACCOUNTS.stream()
                .filter(account -> accounts.findByCompanyIdAndCode(companyId, account.code()).isEmpty())
                .map(account -> new AccountingAccount(companyId, account.code(), account.name(), account.kind(), true))
                .toList();
        if (!missing.isEmpty()) accounts.saveAll(missing);
    }

    private Map<String, AccountingAccount> defaultAccountMap(UUID companyId) {
        return accounts.findAllByCompanyIdAndActiveTrueOrderByCode(companyId).stream()
                .collect(java.util.stream.Collectors.toMap(AccountingAccount::getCode, account -> account));
    }

    private AccountingInboxResponse toInboxResponse(AccountingInboxItem item, Map<String, AccountingAccount> defaults) {
        boolean creditNote = "RECTIFYING_INVOICE".equals(item.getSourceType());
        AccountingAccount customer = requiredDefault(defaults, "430000");
        AccountingAccount salesAccount = requiredDefault(defaults, "700000");
        AccountingAccount taxAccount = requiredDefault(defaults, "477000");
        List<AccountingInboxResponse.SuggestedLine> lines = new ArrayList<>();
        lines.add(suggestion(creditNote ? salesAccount : customer,
                creditNote ? item.getNetAmount() : item.getTotalAmount(), BigDecimal.ZERO));
        if (creditNote && item.getTaxAmount().signum() != 0) {
            lines.add(suggestion(taxAccount, item.getTaxAmount(), BigDecimal.ZERO));
        }
        if (creditNote) {
            lines.add(suggestion(customer, BigDecimal.ZERO, item.getTotalAmount()));
        } else {
            lines.add(suggestion(salesAccount, BigDecimal.ZERO, item.getNetAmount()));
            if (item.getTaxAmount().signum() != 0) lines.add(suggestion(taxAccount, BigDecimal.ZERO, item.getTaxAmount()));
        }
        return new AccountingInboxResponse(item.getId(), item.getSourceType(), item.getSourceId(),
                item.getSourceNumber(), item.getSourceDate(), item.getSourceStatus(), item.isIssued(),
                item.getCounterpartyCode(), item.getCounterpartyName(), item.getCurrency(), item.getNetAmount(),
                item.getTaxAmount(), item.getTotalAmount(), List.copyOf(lines));
    }

    private static AccountingInboxResponse.SuggestedLine suggestion(AccountingAccount account,
                                                                     BigDecimal debit, BigDecimal credit) {
        return new AccountingInboxResponse.SuggestedLine(account.getId(), account.getCode(), account.getName(), debit, credit);
    }

    private static AccountingAccount requiredDefault(Map<String, AccountingAccount> defaults, String code) {
        AccountingAccount account = defaults.get(code);
        if (account == null) throw new BusinessRuleException("Falta la cuenta contable base " + code + ".");
        return account;
    }

    private static BigDecimal money(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
    private record DefaultAccount(String code, String name, AccountKind kind) {}
    private record ResolvedLine(AccountingAccount account, String description, BigDecimal debit, BigDecimal credit) {}
}
