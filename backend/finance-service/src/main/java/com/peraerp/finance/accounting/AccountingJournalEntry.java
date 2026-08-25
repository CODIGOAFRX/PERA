package com.peraerp.finance.accounting;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "accounting_journal_entries")
public class AccountingJournalEntry extends CompanyScopedEntity {
    @Column(name = "entry_date", nullable = false) private LocalDate entryDate;
    @Column(nullable = false, length = 300) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private JournalStatus status;
    @Column(name = "source_type", length = 40) private String sourceType;
    @Column(name = "source_id") private UUID sourceId;
    @Column(name = "source_number", length = 80) private String sourceNumber;
    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<AccountingJournalLine> lines = new ArrayList<>();

    protected AccountingJournalEntry() {}

    public AccountingJournalEntry(UUID companyId, LocalDate entryDate, String description,
                                  String sourceType, UUID sourceId, String sourceNumber) {
        super(companyId);
        this.entryDate = entryDate;
        this.description = description;
        this.status = JournalStatus.POSTED;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.sourceNumber = sourceNumber;
    }

    public void addLine(AccountingAccount account, String description, java.math.BigDecimal debit,
                        java.math.BigDecimal credit) {
        lines.add(new AccountingJournalLine(getCompanyId(), this, lines.size() + 1, account, description, debit, credit));
    }

    public LocalDate getEntryDate() { return entryDate; }
    public String getDescription() { return description; }
    public JournalStatus getStatus() { return status; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getSourceNumber() { return sourceNumber; }
    public List<AccountingJournalLine> getLines() { return List.copyOf(lines); }
}
