package com.peraerp.finance.currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExchangeRateResponse(UUID id, String baseCode, String quoteCode, BigDecimal rate,
                                   LocalDate rateDate, String source, boolean active) {
    static ExchangeRateResponse from(ExchangeRate rate) {
        return new ExchangeRateResponse(rate.getId(), rate.getBaseCode(), rate.getQuoteCode(), rate.getRate(),
                rate.getRateDate(), rate.getSource(), rate.isActive());
    }
}
