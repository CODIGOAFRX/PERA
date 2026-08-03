package com.peraerp.masterdata.party;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

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
        super(companyId);
        this.code = code;
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.taxId = taxId;
        this.phone = phone;
        this.email = email;
        this.observations = observations;
    }

    public void update(String legalName, String tradeName, String taxId, String phone, String email,
                       String observations, boolean active) {
        this.legalName = legalName;
        this.tradeName = tradeName;
        this.taxId = taxId;
        this.phone = phone;
        this.email = email;
        this.observations = observations;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getLegalName() { return legalName; }
    public String getTradeName() { return tradeName; }
    public String getTaxId() { return taxId; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getWebsite() { return website; }
    public String getObservations() { return observations; }
    public boolean isActive() { return active; }
}
