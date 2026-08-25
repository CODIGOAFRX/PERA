package com.peraerp.finance.accounting;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounting_inbox_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_accounting_inbox_source", columnNames = {"company_id", "source_type", "source_id"}))
public class AccountingInboxItem extends CompanyScopedEntity {
    @Column(name = "source_type", nullable = false, length = 40) private String sourceType;
    @Column(name = "source_id", nullable = false) private UUID sourceId;
    @Column(name = "source_number", nullable = false, length = 80) private String sourceNumber;
    @Column(name = "source_date", nullable = false) private LocalDate sourceDate;
    @Column(name = "source_status", nullable = false, length = 30) private String sourceStatus;
    @Column(name = "counterparty_code", length = 60) private String counterpartyCode;
    @Column(name = "counterparty_name", nullable = false, length = 180) private String counterpartyName;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "net_amount", nullable = false, precision = 19, scale = 4) private BigDecimal netAmount;
    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4) private BigDecimal taxAmount;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4) private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING) @Column(name = "inbox_status", nullable = false, length = 20)
    private AccountingInboxStatus inboxStatus = AccountingInboxStatus.PENDING;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "journal_entry_id")
    private AccountingJournalEntry journalEntry;

    protected AccountingInboxItem() {}

    public AccountingInboxItem(UUID companyId, SalesInvoiceSnapshot snapshot) {
        super(companyId);
        this.sourceType = snapshot.type();
        this.sourceId = snapshot.id();
        update(snapshot);
    }

    public void update(SalesInvoiceSnapshot snapshot) {
        if (inboxStatus != AccountingInboxStatus.PENDING) return;
        sourceNumber = snapshot.number();
        sourceDate = snapshot.issueDate();
        sourceStatus = snapshot.status();
        counterpartyCode = snapshot.customerCode();
        counterpartyName = snapshot.customerName();
        currency = snapshot.baseCurrency();
        netAmount = snapshot.baseNetAmount();
        taxAmount = snapshot.baseTaxAmount();
        totalAmount = snapshot.baseTotalAmount();
        if ("CANCELLED".equals(snapshot.status())) inboxStatus = AccountingInboxStatus.DISMISSED;
    }

    public void markPosted(AccountingJournalEntry entry) {
        this.journalEntry = entry;
        this.inboxStatus = AccountingInboxStatus.POSTED;
    }

    public boolean isIssued() { return !"DRAFT".equals(sourceStatus); }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getSourceNumber() { return sourceNumber; }
    public LocalDate getSourceDate() { return sourceDate; }
    public String getSourceStatus() { return sourceStatus; }
    public String getCounterpartyCode() { return counterpartyCode; }
    public String getCounterpartyName() { return counterpartyName; }
    public String getCurrency() { return currency; }
    public BigDecimal getNetAmount() { return netAmount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public AccountingInboxStatus getInboxStatus() { return inboxStatus; }
}
