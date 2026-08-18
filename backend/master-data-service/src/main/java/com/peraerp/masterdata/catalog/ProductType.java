package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "product_types", uniqueConstraints = @UniqueConstraint(name = "uk_product_type_code", columnNames = {"company_id", "code"}))
public class ProductType extends CompanyScopedEntity {
    @Column(name = "supertype_id")
    private UUID supertypeId;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false)
    private boolean active = true;
    protected ProductType() {}

    public ProductType(UUID companyId, String code, String name) {
        this(companyId, null, code, name, true);
    }

    public ProductType(UUID companyId, UUID supertypeId, String code, String name, boolean active) {
        super(companyId);
        this.supertypeId = supertypeId;
        this.code = code;
        this.name = name;
        this.active = active;
    }

    public void update(UUID supertypeId, String name, boolean active) {
        this.supertypeId = supertypeId;
        this.name = name;
        this.active = active;
    }

    public UUID getSupertypeId() { return supertypeId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
