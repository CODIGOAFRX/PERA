package com.peraerp.finance.currency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRateRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String baseCode,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String quoteCode,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal rate,
        @NotNull LocalDate rateDate,
        @NotBlank @Size(max = 120) String source,
        boolean active
) {
}
