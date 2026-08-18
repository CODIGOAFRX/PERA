package com.peraerp.masterdata.packaging;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "packaging_types", uniqueConstraints =
        @UniqueConstraint(name = "uk_packaging_type_code", columnNames = {"company_id", "code"}))
public class PackagingType extends CompanyScopedEntity {
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "internal_length", precision = 15, scale = 4)
    private BigDecimal internalLength;
    @Column(name = "internal_width", precision = 15, scale = 4)
    private BigDecimal internalWidth;
    @Column(name = "internal_height", precision = 15, scale = 4)
    private BigDecimal internalHeight;
    @Column(name = "external_length", precision = 15, scale = 4)
    private BigDecimal externalLength;
    @Column(name = "external_width", precision = 15, scale = 4)
    private BigDecimal externalWidth;
    @Column(name = "external_height", precision = 15, scale = 4)
    private BigDecimal externalHeight;
    @Column(name = "tare_weight", precision = 15, scale = 4)
    private BigDecimal tareWeight;
    @Column(name = "maximum_weight", precision = 15, scale = 4)
    private BigDecimal maximumWeight;
    @Column(name = "maximum_volume", precision = 19, scale = 6)
    private BigDecimal maximumVolume;
    @Column(nullable = false)
    private boolean returnable;
    @Column(nullable = false)
    private boolean active = true;

    protected PackagingType() {}

    public PackagingType(UUID companyId, String code, String name, String description,
                         BigDecimal internalLength, BigDecimal internalWidth, BigDecimal internalHeight,
                         BigDecimal externalLength, BigDecimal externalWidth, BigDecimal externalHeight,
                         BigDecimal tareWeight, BigDecimal maximumWeight, BigDecimal maximumVolume,
                         boolean returnable, boolean active) {
        super(companyId);
        this.code = code;
        update(name, description, internalLength, internalWidth, internalHeight, externalLength, externalWidth,
                externalHeight, tareWeight, maximumWeight, maximumVolume, returnable, active);
    }

    public void update(String name, String description, BigDecimal internalLength, BigDecimal internalWidth,
                       BigDecimal internalHeight, BigDecimal externalLength, BigDecimal externalWidth,
                       BigDecimal externalHeight, BigDecimal tareWeight, BigDecimal maximumWeight,
                       BigDecimal maximumVolume, boolean returnable, boolean active) {
        this.name = name;
        this.description = description;
        this.internalLength = internalLength;
        this.internalWidth = internalWidth;
        this.internalHeight = internalHeight;
        this.externalLength = externalLength;
        this.externalWidth = externalWidth;
        this.externalHeight = externalHeight;
        this.tareWeight = tareWeight;
        this.maximumWeight = maximumWeight;
        this.maximumVolume = maximumVolume;
        this.returnable = returnable;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getInternalLength() { return internalLength; }
    public BigDecimal getInternalWidth() { return internalWidth; }
    public BigDecimal getInternalHeight() { return internalHeight; }
    public BigDecimal getExternalLength() { return externalLength; }
    public BigDecimal getExternalWidth() { return externalWidth; }
    public BigDecimal getExternalHeight() { return externalHeight; }
    public BigDecimal getTareWeight() { return tareWeight; }
    public BigDecimal getMaximumWeight() { return maximumWeight; }
    public BigDecimal getMaximumVolume() { return maximumVolume; }
    public boolean isReturnable() { return returnable; }
    public boolean isActive() { return active; }
}
