package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "product_families", uniqueConstraints = @UniqueConstraint(name = "uk_product_family_code", columnNames = {"company_id", "code"}))
public class ProductFamily extends CompanyScopedEntity {
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false)
    private boolean active = true;
    protected ProductFamily() {}
}
