package com.peraerp.identity.company;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "company_settings", uniqueConstraints =
        @UniqueConstraint(name = "uk_company_settings_company", columnNames = "company_id"))
public class CompanySettings extends CompanyScopedEntity {

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;
    @Column(nullable = false, length = 35)
    private String locale;
    @Column(nullable = false, length = 64)
    private String timezone;
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;
    @Column(name = "display_name", nullable = false, length = 180)
    private String displayName;
    @Column(name = "logo_storage_key", length = 500)
    private String logoStorageKey;
    @Column(name = "logo_content_type", length = 40)
    private String logoContentType;
    @Column(name = "logo_sha256", length = 64)
    private String logoSha256;
    @Column(name = "contact_email", length = 180)
    private String contactEmail;
    @Column(name = "invoice_email", length = 180)
    private String invoiceEmail;
    @Column(name = "reply_to_email", length = 180)
    private String replyToEmail;
    @Column(length = 40)
    private String phone;
    @Column(length = 240)
    private String website;
    @Column(name = "address_line1", length = 200)
    private String addressLine1;
    @Column(name = "address_line2", length = 200)
    private String addressLine2;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(length = 100)
    private String city;
    @Column(length = 100)
    private String region;

    protected CompanySettings() {
    }

    private CompanySettings(UUID companyId, String displayName) {
        super(companyId);
        this.countryCode = "ES";
        this.locale = "es-ES";
        this.timezone = "Europe/Madrid";
        this.baseCurrency = "EUR";
        this.displayName = displayName;
    }

    public static CompanySettings defaults(UUID companyId, String displayName) {
        return new CompanySettings(companyId, displayName);
    }

    public void updateProfile(String countryCode, String locale, String timezone, String baseCurrency,
                              String displayName, String contactEmail, String invoiceEmail, String replyToEmail,
                              String phone, String website, String addressLine1, String addressLine2,
                              String postalCode, String city, String region) {
        this.countryCode = countryCode;
        this.locale = locale;
        this.timezone = timezone;
        this.baseCurrency = baseCurrency;
        this.displayName = displayName;
        this.contactEmail = contactEmail;
        this.invoiceEmail = invoiceEmail;
        this.replyToEmail = replyToEmail;
        this.phone = phone;
        this.website = website;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.postalCode = postalCode;
        this.city = city;
        this.region = region;
    }

    public void updateLogo(String storageKey, String contentType, String sha256) {
        this.logoStorageKey = storageKey;
        this.logoContentType = contentType;
        this.logoSha256 = sha256;
    }

    public void clearLogo() {
        updateLogo(null, null, null);
    }

    public String getCountryCode() { return countryCode; }
    public String getLocale() { return locale; }
    public String getTimezone() { return timezone; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getDisplayName() { return displayName; }
    public String getLogoStorageKey() { return logoStorageKey; }
    public String getLogoContentType() { return logoContentType; }
    public String getLogoSha256() { return logoSha256; }
    public String getContactEmail() { return contactEmail; }
    public String getInvoiceEmail() { return invoiceEmail; }
    public String getReplyToEmail() { return replyToEmail; }
    public String getPhone() { return phone; }
    public String getWebsite() { return website; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getPostalCode() { return postalCode; }
    public String getCity() { return city; }
    public String getRegion() { return region; }
}
