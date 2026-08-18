package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "carriers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_carrier_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_carrier_company_id", columnNames = {"company_id", "id"})
})
public class Carrier extends CompanyScopedEntity {

    @Column(nullable = false, length = 60, updatable = false)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CarrierOwnership ownership;
    @Column(name = "tax_identifier", length = 40)
    private String taxIdentifier;
    @Column(name = "external_identifier", length = 100)
    private String externalIdentifier;
    @Column(name = "contact_name", length = 180)
    private String contactName;
    @Column(name = "contact_email", length = 254)
    private String contactEmail;
    @Column(name = "contact_phone", length = 40)
    private String contactPhone;
    @Column(nullable = false)
    private boolean active = true;

    protected Carrier() {
    }

    public Carrier(UUID companyId, String code, String name, CarrierOwnership ownership) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.ownership = ownership;
    }

    public void update(String name, CarrierOwnership ownership, String taxIdentifier, String externalIdentifier,
                       String contactName, String contactEmail, String contactPhone, boolean active) {
        this.name = name;
        this.ownership = ownership;
        this.taxIdentifier = taxIdentifier;
        this.externalIdentifier = externalIdentifier;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public CarrierOwnership getOwnership() { return ownership; }
    public String getTaxIdentifier() { return taxIdentifier; }
    public String getExternalIdentifier() { return externalIdentifier; }
    public String getContactName() { return contactName; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public boolean isActive() { return active; }
}
