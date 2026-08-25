package com.peraerp.finance.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountingInboxResponse(UUID id, String sourceType, UUID sourceId, String sourceNumber,
                                      LocalDate sourceDate, String sourceStatus, boolean readyToPost,
                                      String counterpartyCode, String counterpartyName, String currency,
                                      BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                                      List<SuggestedLine> suggestedLines) {
    public record SuggestedLine(UUID accountId, String accountCode, String accountName,
                                BigDecimal debit, BigDecimal credit) {}
}
