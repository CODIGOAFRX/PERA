package com.peraerp.finance.currency;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "currencies", uniqueConstraints =
        @UniqueConstraint(name = "uk_currency_code", columnNames = {"company_id", "code"}))
public class CurrencyDefinition extends CompanyScopedEntity {

    @Column(nullable = false, length = 3, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 12)
    private String symbol;

    @Column(name = "decimal_places", nullable = false)
    private int decimalPlaces;

    @Column(name = "base_currency", nullable = false)
    private boolean baseCurrency;

    @Column(nullable = false)
    private boolean active;

    protected CurrencyDefinition() {
    }

    public CurrencyDefinition(UUID companyId, String code, String name, String symbol, int decimalPlaces,
                              boolean baseCurrency, boolean active) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
        this.baseCurrency = baseCurrency;
        this.active = active;
    }

    public void update(String name, String symbol, int decimalPlaces, boolean baseCurrency, boolean active) {
        this.name = name;
        this.symbol = symbol;
        this.decimalPlaces = decimalPlaces;
        this.baseCurrency = baseCurrency;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public int getDecimalPlaces() { return decimalPlaces; }
    public boolean isBaseCurrency() { return baseCurrency; }
    public boolean isActive() { return active; }
}
