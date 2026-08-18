package com.peraerp.sales.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExchangeRateClient {
    ResolvedExchangeRate resolve(String fromCurrency, String toCurrency, LocalDate date);

    record ResolvedExchangeRate(BigDecimal rate, LocalDate rateDate, String source) {
    }
}
