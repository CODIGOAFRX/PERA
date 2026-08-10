package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "product_supertypes", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_supertype_code", columnNames = {"company_id", "code"}))
public class ProductSupertype extends CompanyScopedEntity {
    @Column(name = "nature_id", nullable = false)
    private UUID natureId;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false)
    private boolean active = true;

    protected ProductSupertype() {}

    public ProductSupertype(UUID companyId, UUID natureId, String code, String name, boolean active) {
        super(companyId);
        this.natureId = natureId;
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public void update(UUID natureId, String name, boolean active) {
        this.natureId = natureId;
        this.name = name;
        this.active = active;
    }

    public UUID getNatureId() { return natureId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
