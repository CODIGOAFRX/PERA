package com.peraerp.finance.currency;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exchange_rates", uniqueConstraints = @UniqueConstraint(name = "uk_exchange_rate",
        columnNames = {"company_id", "base_code", "quote_code", "rate_date", "source"}))
public class ExchangeRate extends CompanyScopedEntity {

    @Column(name = "base_code", nullable = false, length = 3, updatable = false)
    private String baseCode;

    @Column(name = "quote_code", nullable = false, length = 3, updatable = false)
    private String quoteCode;

    @Column(nullable = false, precision = 19, scale = 10)
    private BigDecimal rate;

    @Column(name = "rate_date", nullable = false, updatable = false)
    private LocalDate rateDate;

    @Column(nullable = false, length = 120, updatable = false)
    private String source;

    @Column(nullable = false)
    private boolean active;

    protected ExchangeRate() {
    }

    public ExchangeRate(UUID companyId, String baseCode, String quoteCode, BigDecimal rate, LocalDate rateDate,
                        String source, boolean active) {
        super(companyId);
        this.baseCode = baseCode;
        this.quoteCode = quoteCode;
        this.rate = rate;
        this.rateDate = rateDate;
        this.source = source;
        this.active = active;
    }

    public void update(BigDecimal rate, boolean active) {
        this.rate = rate;
        this.active = active;
    }

    public String getBaseCode() { return baseCode; }
    public String getQuoteCode() { return quoteCode; }
    public BigDecimal getRate() { return rate; }
    public LocalDate getRateDate() { return rateDate; }
    public String getSource() { return source; }
    public boolean isActive() { return active; }
}
