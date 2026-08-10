package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_lists", uniqueConstraints = @UniqueConstraint(name = "uk_price_list_code", columnNames = {"company_id", "code"}))
public class PriceList extends CompanyScopedEntity {
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false, length = 3)
    private String currency = "EUR";
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom = LocalDate.of(1970, 1, 1);
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(nullable = false)
    private int priority;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PricingScope scope = PricingScope.GENERAL;
    @Column(name = "customer_id")
    private UUID customerId;
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
    @Column(name = "parent_price_list_id")
    private UUID parentPriceListId;
    @Column(name = "general_surcharge_percentage", precision = 9, scale = 4)
    private BigDecimal generalSurchargePercentage;
    @Column(name = "energy_surcharge_percentage", precision = 9, scale = 4)
    private BigDecimal energySurchargePercentage;
    @Column(name = "minimum_billing_amount", precision = 19, scale = 4)
    private BigDecimal minimumBillingAmount;
    @Column(name = "unit_multiple", precision = 19, scale = 6)
    private BigDecimal unitMultiple;
    @Column(name = "minimum_per_piece", precision = 19, scale = 4)
    private BigDecimal minimumPerPiece;
    @Column(nullable = false)
    private boolean active = true;
    protected PriceList() {}

    public PriceList(UUID companyId, String code, String name, String currency) {
        this(companyId, code, name, currency, LocalDate.of(1970, 1, 1), null, true, 0,
                PricingScope.GENERAL, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public PriceList(UUID companyId, String code, String name, String currency, LocalDate validFrom,
                     LocalDate validUntil, boolean active, int priority, PricingScope scope, UUID customerId,
                     UUID productNatureId, UUID productSupertypeId, UUID productTypeId, UUID productGroupId,
                     UUID productId, UUID parentPriceListId, BigDecimal generalSurchargePercentage,
                     BigDecimal energySurchargePercentage, BigDecimal minimumBillingAmount,
                     BigDecimal unitMultiple, BigDecimal minimumPerPiece) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.currency = currency;
        update(name, currency, validFrom, validUntil, active, priority, scope, customerId, productNatureId,
                productSupertypeId, productTypeId, productGroupId, productId, parentPriceListId,
                generalSurchargePercentage, energySurchargePercentage, minimumBillingAmount, unitMultiple,
                minimumPerPiece);
    }

    public void update(String name, String currency, LocalDate validFrom, LocalDate validUntil, boolean active,
                       int priority, PricingScope scope, UUID customerId, UUID productNatureId,
                       UUID productSupertypeId, UUID productTypeId, UUID productGroupId, UUID productId,
                       UUID parentPriceListId, BigDecimal generalSurchargePercentage,
                       BigDecimal energySurchargePercentage, BigDecimal minimumBillingAmount,
                       BigDecimal unitMultiple, BigDecimal minimumPerPiece) {
        this.name = name;
        this.currency = currency;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = active;
        this.priority = priority;
        this.scope = scope;
        this.customerId = customerId;
        this.productNatureId = productNatureId;
        this.productSupertypeId = productSupertypeId;
        this.productTypeId = productTypeId;
        this.productGroupId = productGroupId;
        this.productId = productId;
        this.parentPriceListId = parentPriceListId;
        this.generalSurchargePercentage = generalSurchargePercentage;
        this.energySurchargePercentage = energySurchargePercentage;
        this.minimumBillingAmount = minimumBillingAmount;
        this.unitMultiple = unitMultiple;
        this.minimumPerPiece = minimumPerPiece;
    }

    public boolean isEffectiveOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public int getPriority() { return priority; }
    public PricingScope getScope() { return scope; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProductNatureId() { return productNatureId; }
    public UUID getProductSupertypeId() { return productSupertypeId; }
    public UUID getProductTypeId() { return productTypeId; }
    public UUID getProductGroupId() { return productGroupId; }
    public UUID getProductId() { return productId; }
    public UUID getParentPriceListId() { return parentPriceListId; }
    public BigDecimal getGeneralSurchargePercentage() { return generalSurchargePercentage; }
    public BigDecimal getEnergySurchargePercentage() { return energySurchargePercentage; }
    public BigDecimal getMinimumBillingAmount() { return minimumBillingAmount; }
    public BigDecimal getUnitMultiple() { return unitMultiple; }
    public BigDecimal getMinimumPerPiece() { return minimumPerPiece; }
    public boolean isActive() { return active; }
}
