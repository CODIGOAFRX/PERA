package com.peraerp.finance.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CurrencyConversionResponse(
        BigDecimal sourceAmount,
        String sourceCurrency,
        BigDecimal targetAmount,
        String targetCurrency,
        BigDecimal exchangeRate,
        LocalDate requestedDate,
        LocalDate rateDate,
        String rateSource,
        UUID exchangeRateId,
        boolean inverseRate
) {
}
