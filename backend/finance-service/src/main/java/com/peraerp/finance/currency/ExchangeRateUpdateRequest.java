package com.peraerp.finance.currency;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ExchangeRateUpdateRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal rate,
        boolean active
) {
}
