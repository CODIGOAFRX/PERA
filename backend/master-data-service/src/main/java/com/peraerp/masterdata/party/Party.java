package com.peraerp.masterdata.party;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "parties", uniqueConstraints = @UniqueConstraint(name = "uk_party_company_code", columnNames = {"company_id", "code"}))
public class Party extends CompanyScopedEntity {

    @Column(nullable = false, length = 40)
    private String code;
    @Column(name = "legal_name", nullable = false, length = 180)
    private String legalName;
    @Column(name = "trade_name", length = 180)
    private String tradeName;
    @Column(name = "tax_id", length = 30)
    private String taxId;
    @Enumerated(EnumType.STRING) @Column(name = "tax_identification_type", length = 20)
    private TaxIdentificationType taxIdentificationType;
    @Column(name = "tax_country_code", length = 2)
    private String taxCountryCode;
    @Column(length = 40)
    private String phone;
    @Column(length = 180)
    private String email;
    @Column(length = 240)
    private String website;
    @Column(columnDefinition = "text")
    private String observations;
    @Column(nullable = false)
    private boolean active = true;

    protected Party() {}

    public Party(UUID companyId, String code, String legalName, String tradeName, String taxId,
                 String phone, String email, String observations) {
        this(companyId, code, legalName, tradeName, taxId, null, null, phone, email, observations);
    }

    public Party(UUID companyId, String code, String legalName, String tradeName, String taxId,
                 TaxIdentificationType taxIdentificationType, String taxCountryCode,
                 String phone, String email, String observations) {
        super(companyId);
        this.code = code;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.phone = phone;
        this.email = email;
        this.observations = observations;
        applyTaxIdentification(taxId, taxIdentificationType, taxCountryCode);
    }

    public void update(String legalName, String tradeName, String taxId, String phone, String email,
                       String observations, boolean active) {
        update(legalName, tradeName, taxId, taxIdentificationType, taxCountryCode, phone, email, observations, active);
    }

    public void update(String legalName, String tradeName, String taxId,
                       TaxIdentificationType taxIdentificationType, String taxCountryCode,
                       String phone, String email, String observations, boolean active) {
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.phone = phone;
        this.email = email;
        this.observations = observations;
        this.active = active;
        applyTaxIdentification(taxId, taxIdentificationType, taxCountryCode);
    }

    /**
     * Normaliza el identificador fiscal y sus dos acompañantes.
     *
     * <p>Si hay identificador pero no se dice de qué tipo es, se asume {@link TaxIdentificationType#NIF}
     * español. Es el caso mayoritario en un ERP español y evita que un alta desde una pantalla
     * antigua deje el tercero sin clasificar, que es lo que rompería después el registro de
     * facturación. Si no hay identificador, no puede haber ni tipo ni país.</p>
     */
    private void applyTaxIdentification(String taxId, TaxIdentificationType type, String countryCode) {
        String normalizedTaxId = taxId == null || taxId.isBlank() ? null : taxId.trim().toUpperCase(Locale.ROOT);
        if (normalizedTaxId == null) {
            this.taxId = null;
            this.taxIdentificationType = null;
            this.taxCountryCode = null;
            return;
        }
        this.taxId = normalizedTaxId;
        this.taxIdentificationType = type == null ? TaxIdentificationType.NIF : type;
        String normalizedCountry = countryCode == null || countryCode.isBlank()
                ? null : countryCode.trim().toUpperCase(Locale.ROOT);
        this.taxCountryCode = normalizedCountry == null && this.taxIdentificationType == TaxIdentificationType.NIF
                ? "ES" : normalizedCountry;
    }

    public String getCode() { return code; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getTaxId() { return taxId; }
    public TaxIdentificationType getTaxIdentificationType() { return taxIdentificationType; }
    public String getTaxCountryCode() { return taxCountryCode; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getWebsite() { return website; }
    public String getObservations() { return observations; }
    public boolean isActive() { return active; }
}
