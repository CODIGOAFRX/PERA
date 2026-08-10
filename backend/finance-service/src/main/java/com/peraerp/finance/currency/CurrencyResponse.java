package com.peraerp.finance.currency;

import java.util.UUID;

public record CurrencyResponse(UUID id, String code, String name, String symbol, int decimalPlaces,
                               boolean baseCurrency, boolean active) {
    static CurrencyResponse from(CurrencyDefinition currency) {
        return new CurrencyResponse(currency.getId(), currency.getCode(), currency.getName(), currency.getSymbol(),
                currency.getDecimalPlaces(), currency.isBaseCurrency(), currency.isActive());
    }
}
