package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "product_natures", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_nature_code", columnNames = {"company_id", "code"}))
public class ProductNature extends CompanyScopedEntity {
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false)
    private boolean active = true;

    protected ProductNature() {}

    public ProductNature(UUID companyId, String code, String name, boolean active) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
