package com.peraerp.masterdata.packaging;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "product_packaging")
public class ProductPackaging extends CompanyScopedEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "packaging_type_id", nullable = false)
    private UUID packagingTypeId;
    @Column(length = 80)
    private String code;
    @Column(name = "units_per_package", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitsPerPackage;
    private Integer levels;
    @Column(name = "units_per_level", precision = 19, scale = 6)
    private BigDecimal unitsPerLevel;
    @Column(precision = 15, scale = 4)
    private BigDecimal length;
    @Column(precision = 15, scale = 4)
    private BigDecimal width;
    @Column(precision = 15, scale = 4)
    private BigDecimal height;
    @Column(name = "gross_weight", precision = 15, scale = 4)
    private BigDecimal grossWeight;
    @Column(name = "default_packaging", nullable = false)
    private boolean defaultPackaging;
    @Column(nullable = false)
    private boolean active = true;

    protected ProductPackaging() {}

    public ProductPackaging(UUID companyId, UUID productId, UUID packagingTypeId, String code,
                            BigDecimal unitsPerPackage, Integer levels, BigDecimal unitsPerLevel,
                            BigDecimal length, BigDecimal width, BigDecimal height, BigDecimal grossWeight,
                            boolean defaultPackaging, boolean active) {
        super(companyId);
        this.productId = productId;
        this.packagingTypeId = packagingTypeId;
        this.code = code;
        update(unitsPerPackage, levels, unitsPerLevel, length, width, height, grossWeight, defaultPackaging,
                active);
    }

    public void update(BigDecimal unitsPerPackage, Integer levels, BigDecimal unitsPerLevel,
                       BigDecimal length, BigDecimal width, BigDecimal height, BigDecimal grossWeight,
                       boolean defaultPackaging, boolean active) {
        this.unitsPerPackage = unitsPerPackage;
        this.levels = levels;
        this.unitsPerLevel = unitsPerLevel;
        this.length = length;
        this.width = width;
        this.height = height;
        this.grossWeight = grossWeight;
        this.defaultPackaging = defaultPackaging;
        this.active = active;
    }

    public UUID getProductId() { return productId; }
    public UUID getPackagingTypeId() { return packagingTypeId; }
    public String getCode() { return code; }
    public BigDecimal getUnitsPerPackage() { return unitsPerPackage; }
    public Integer getLevels() { return levels; }
    public BigDecimal getUnitsPerLevel() { return unitsPerLevel; }
    public BigDecimal getLength() { return length; }
    public BigDecimal getWidth() { return width; }
    public BigDecimal getHeight() { return height; }
    public BigDecimal getGrossWeight() { return grossWeight; }
    public boolean isDefaultPackaging() { return defaultPackaging; }
    public boolean isActive() { return active; }
}
