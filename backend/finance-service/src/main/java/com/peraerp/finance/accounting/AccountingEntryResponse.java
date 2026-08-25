package com.peraerp.finance.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountingEntryResponse(UUID id, LocalDate entryDate, String description, JournalStatus status,
                                      String sourceType, String sourceNumber, BigDecimal total,
                                      List<Line> lines) {
    public record Line(UUID accountId, String accountCode, String accountName, String description,
                       BigDecimal debit, BigDecimal credit) {}

    static AccountingEntryResponse from(AccountingJournalEntry entry) {
        List<Line> lines = entry.getLines().stream().map(line -> new Line(line.getAccount().getId(),
                line.getAccount().getCode(), line.getAccount().getName(), line.getDescription(), line.getDebit(),
                line.getCredit())).toList();
        BigDecimal total = lines.stream().map(Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AccountingEntryResponse(entry.getId(), entry.getEntryDate(), entry.getDescription(),
                entry.getStatus(), entry.getSourceType(), entry.getSourceNumber(), total, lines);
    }
}
