package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tax_codes", uniqueConstraints = @UniqueConstraint(
        name = "uk_tax_code_country", columnNames = {"company_id", "country_code", "code"}))
public class TaxCode extends CompanyScopedEntity {
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(nullable = false)
    private boolean exempt;
    @Column(nullable = false)
    private boolean active = true;

    protected TaxCode() {}

    public TaxCode(UUID companyId, String countryCode, String code, String name, BigDecimal percentage,
                   LocalDate validFrom, LocalDate validUntil, boolean exempt, boolean active) {
        super(companyId);
        this.countryCode = countryCode;
        this.code = code;
        this.name = name;
        this.percentage = percentage;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.exempt = exempt;
        this.active = active;
    }

    public void update(String countryCode, String name, BigDecimal percentage, LocalDate validFrom,
                       LocalDate validUntil, boolean exempt, boolean active) {
        this.countryCode = countryCode;
        this.name = name;
        this.percentage = percentage;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.exempt = exempt;
        this.active = active;
    }

    public boolean isApplicableOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }

    public String getCountryCode() { return countryCode; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getPercentage() { return percentage; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public boolean isExempt() { return exempt; }
    public boolean isActive() { return active; }
}
