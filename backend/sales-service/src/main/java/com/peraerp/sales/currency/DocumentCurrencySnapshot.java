package com.peraerp.sales.currency;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DocumentCurrencySnapshot(String baseCurrency, BigDecimal exchangeRate,
                                       LocalDate rateDate, String source) {
}
