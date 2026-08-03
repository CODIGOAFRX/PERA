package com.peraerp.identity.company;

import com.peraerp.platform.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "companies", uniqueConstraints = @UniqueConstraint(name = "uk_company_code", columnNames = "code"))
public class Company extends AuditableEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "tax_id", length = 30)
    private String taxId;

    @Column(nullable = false)
    private boolean active = true;

    protected Company() {
    }

    public Company(String code, String name, String taxId) {
        this.code = code;
        this.name = name;
        this.taxId = taxId;
    }

    public void update(String name, String taxId, boolean active) {
        this.name = name;
        this.taxId = taxId;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getTaxId() { return taxId; }
    public boolean isActive() { return active; }
}
