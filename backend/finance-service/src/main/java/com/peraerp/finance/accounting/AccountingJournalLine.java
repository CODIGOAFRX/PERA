package com.peraerp.finance.accounting;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounting_journal_lines")
public class AccountingJournalLine extends CompanyScopedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "entry_id")
    private AccountingJournalEntry entry;
    @Column(name = "line_order", nullable = false) private int lineOrder;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "account_id")
    private AccountingAccount account;
    @Column(length = 300) private String description;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal debit;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal credit;

    protected AccountingJournalLine() {}

    AccountingJournalLine(UUID companyId, AccountingJournalEntry entry, int lineOrder, AccountingAccount account,
                          String description, BigDecimal debit, BigDecimal credit) {
        super(companyId);
        this.entry = entry;
        this.lineOrder = lineOrder;
        this.account = account;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
    }

    public int getLineOrder() { return lineOrder; }
    public AccountingAccount getAccount() { return account; }
    public String getDescription() { return description; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
}
