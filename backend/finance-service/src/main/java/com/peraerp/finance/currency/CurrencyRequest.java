package com.peraerp.finance.currency;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CurrencyRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 12) String symbol,
        @Min(0) @Max(6) int decimalPlaces,
        boolean baseCurrency,
        boolean active
) {
}
