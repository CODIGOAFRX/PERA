package com.peraerp.sales.currency;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class DocumentCurrencyService {

    private final CompanyCurrencyClient companyCurrencyClient;
    private final ExchangeRateClient exchangeRateClient;

    public DocumentCurrencyService(CompanyCurrencyClient companyCurrencyClient, ExchangeRateClient exchangeRateClient) {
        this.companyCurrencyClient = companyCurrencyClient;
        this.exchangeRateClient = exchangeRateClient;
    }

    public DocumentCurrencySnapshot resolve(String documentCurrency, LocalDate issueDate) {
        String currency = normalize(documentCurrency);
        String baseCurrency = normalize(companyCurrencyClient.currentBaseCurrency());
        if (currency.equals(baseCurrency)) {
            return new DocumentCurrencySnapshot(baseCurrency, BigDecimal.ONE, issueDate, "IDENTITY");
        }
        ExchangeRateClient.ResolvedExchangeRate rate = exchangeRateClient.resolve(currency, baseCurrency, issueDate);
        if (rate.rate().signum() <= 0) {
            throw new BusinessRuleException("El tipo de cambio debe ser positivo.");
        }
        return new DocumentCurrencySnapshot(baseCurrency, rate.rate(), rate.rateDate(), rate.source());
    }

    private String normalize(String currency) {
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}")) {
            throw new BusinessRuleException("La moneda debe utilizar un código ISO de tres letras.");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
