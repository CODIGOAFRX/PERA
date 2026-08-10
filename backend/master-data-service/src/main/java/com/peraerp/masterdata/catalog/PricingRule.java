package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pricing_rules")
public class PricingRule extends CompanyScopedEntity {
    @Column(name = "price_list_id", nullable = false)
    private UUID priceListId;
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private PricingTargetType targetType;
    @Column(name = "product_nature_id")
    private UUID productNatureId;
    @Column(name = "product_supertype_id")
    private UUID productSupertypeId;
    @Column(name = "product_type_id")
    private UUID productTypeId;
    @Column(name = "product_group_id")
    private UUID productGroupId;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "fixed_price", precision = 19, scale = 4)
    private BigDecimal fixedPrice;
    @Column(name = "discount_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal discountPercentage = BigDecimal.ZERO;
    @Column(name = "surcharge_percentage", nullable = false, precision = 9, scale = 4)
    private BigDecimal surchargePercentage = BigDecimal.ZERO;
    @Column(nullable = false)
    private int priority;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(nullable = false)
    private boolean active = true;

    protected PricingRule() {}

    public PricingRule(UUID companyId, UUID priceListId, PricingTargetType targetType, UUID productNatureId,
                       UUID productSupertypeId, UUID productTypeId, UUID productGroupId, UUID productId,
                       UUID customerId, BigDecimal fixedPrice, BigDecimal discountPercentage,
                       BigDecimal surchargePercentage, int priority, LocalDate validFrom, LocalDate validUntil,
                       boolean active) {
        super(companyId);
        this.priceListId = priceListId;
        this.targetType = targetType;
        this.productNatureId = productNatureId;
        this.productSupertypeId = productSupertypeId;
        this.productTypeId = productTypeId;
        this.productGroupId = productGroupId;
        this.productId = productId;
        this.customerId = customerId;
        update(fixedPrice, discountPercentage, surchargePercentage, priority, validFrom, validUntil, active);
    }

    public void update(BigDecimal fixedPrice, BigDecimal discountPercentage, BigDecimal surchargePercentage,
                       int priority, LocalDate validFrom, LocalDate validUntil, boolean active) {
        this.fixedPrice = fixedPrice;
        this.discountPercentage = discountPercentage;
        this.surchargePercentage = surchargePercentage;
        this.priority = priority;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = active;
    }

    public boolean isEffectiveOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }

    public UUID getPriceListId() { return priceListId; }
    public PricingTargetType getTargetType() { return targetType; }
    public UUID getProductNatureId() { return productNatureId; }
    public UUID getProductSupertypeId() { return productSupertypeId; }
    public UUID getProductTypeId() { return productTypeId; }
    public UUID getProductGroupId() { return productGroupId; }
    public UUID getProductId() { return productId; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getFixedPrice() { return fixedPrice; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public BigDecimal getSurchargePercentage() { return surchargePercentage; }
    public int getPriority() { return priority; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public boolean isActive() { return active; }
}
