package com.peraerp.finance.currency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrencyConversionRequest(
        @NotNull @DecimalMin("0") BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String fromCurrency,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String toCurrency,
        @NotNull LocalDate date
) {
}
