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
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false)
    private boolean active = true;
    protected ProductType() {}
    public ProductType(UUID companyId, String code, String name) { super(companyId); this.code = code; this.name = name; }
    public String getCode() { return code; }
    public String getName() { return name; }
}
