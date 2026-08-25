package com.peraerp.finance.accounting;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountingLineRequest(@NotNull UUID accountId, @Size(max = 300) String description,
                                    @NotNull @DecimalMin("0") BigDecimal debit,
                                    @NotNull @DecimalMin("0") BigDecimal credit) {}
