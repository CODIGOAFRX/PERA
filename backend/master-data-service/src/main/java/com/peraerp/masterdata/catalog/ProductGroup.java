package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "product_groups", uniqueConstraints = @UniqueConstraint(
        name = "uk_product_group_code", columnNames = {"company_id", "code"}))
public class ProductGroup extends CompanyScopedEntity {
    @Column(name = "product_type_id", nullable = false)
    private UUID productTypeId;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false)
    private boolean active = true;

    protected ProductGroup() {}

    public ProductGroup(UUID companyId, UUID productTypeId, String code, String name, boolean active) {
        super(companyId);
        this.productTypeId = productTypeId;
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public void update(UUID productTypeId, String name, boolean active) {
        this.productTypeId = productTypeId;
        this.name = name;
        this.active = active;
    }

    public UUID getProductTypeId() { return productTypeId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
