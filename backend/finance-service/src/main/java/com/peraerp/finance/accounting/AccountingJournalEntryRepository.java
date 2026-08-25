package com.peraerp.finance.accounting;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountingJournalEntryRepository extends JpaRepository<AccountingJournalEntry, UUID> {
    @EntityGraph(attributePaths = {"lines", "lines.account"})
    List<AccountingJournalEntry> findTop100ByCompanyIdOrderByEntryDateDescCreatedAtDesc(UUID companyId);
}
