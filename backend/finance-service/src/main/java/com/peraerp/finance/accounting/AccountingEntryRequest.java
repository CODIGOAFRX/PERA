package com.peraerp.finance.accounting;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AccountingEntryRequest(@NotNull LocalDate entryDate,
                                     @NotBlank @Size(max = 300) String description,
                                     @NotNull @Size(min = 2, max = 50) List<@Valid AccountingLineRequest> lines) {}
