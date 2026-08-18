package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_list_items")
public class PriceListItem extends CompanyScopedEntity {
    @Column(name = "price_list_id", nullable = false)
    private UUID priceListId;
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;
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
    protected PriceListItem() {}

    public PriceListItem(UUID companyId, UUID priceListId, UUID productId, UUID customerId, BigDecimal price,
                         BigDecimal discountPercentage, BigDecimal surchargePercentage, int priority,
                         LocalDate validFrom, LocalDate validUntil, boolean active) {
        super(companyId);
        this.priceListId = priceListId;
        this.productId = productId;
        this.customerId = customerId;
        update(price, discountPercentage, surchargePercentage, priority, validFrom, validUntil, active);
    }

    public void update(BigDecimal price, BigDecimal discountPercentage, BigDecimal surchargePercentage,
                       int priority, LocalDate validFrom, LocalDate validUntil, boolean active) {
        this.price = price;
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
    public UUID getProductId() { return productId; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getDiscountPercentage() { return discountPercentage; }
    public BigDecimal getSurchargePercentage() { return surchargePercentage; }
    public int getPriority() { return priority; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public boolean isActive() { return active; }
}
