package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(name = "uk_product_company_code", columnNames = {"company_id", "code"}))
public class Product extends CompanyScopedEntity {
    @Column(nullable = false, length = 60)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "product_type_id")
    private UUID productTypeId;
    @Column(name = "family_id")
    private UUID familyId;
    @Column(name = "category_id")
    private UUID categoryId;
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_of_measure", nullable = false, length = 30)
    private UnitOfMeasure unitOfMeasure;
    @Column(name = "base_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal basePrice;
    @Column(name = "tax_rate", nullable = false, precision = 7, scale = 4)
    private BigDecimal taxRate;
    @Column(nullable = false)
    private boolean active = true;

    protected Product() {}

    public Product(UUID companyId, String code, String name, String description, UUID productTypeId,
                   UUID familyId, UUID categoryId, UnitOfMeasure unitOfMeasure, BigDecimal basePrice,
                   BigDecimal taxRate) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.description = description;
        this.productTypeId = productTypeId;
        this.familyId = familyId;
        this.categoryId = categoryId;
        this.unitOfMeasure = unitOfMeasure;
        this.basePrice = basePrice;
        this.taxRate = taxRate;
    }

    public void update(String name, String description, UUID productTypeId, UUID familyId, UUID categoryId,
                       UnitOfMeasure unitOfMeasure, BigDecimal basePrice, BigDecimal taxRate, boolean active) {
        this.name = name; this.description = description; this.productTypeId = productTypeId;
        this.familyId = familyId; this.categoryId = categoryId; this.unitOfMeasure = unitOfMeasure;
        this.basePrice = basePrice; this.taxRate = taxRate; this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getProductTypeId() { return productTypeId; }
    public UUID getFamilyId() { return familyId; }
    public UUID getCategoryId() { return categoryId; }
    public UnitOfMeasure getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public boolean isActive() { return active; }
}
