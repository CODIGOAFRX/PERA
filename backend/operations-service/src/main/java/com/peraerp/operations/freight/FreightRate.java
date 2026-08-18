package com.peraerp.operations.freight;

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
@Table(name = "freight_rates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_freight_rate_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_freight_rate_company_id", columnNames = {"company_id", "id"})
})
public class FreightRate extends CompanyScopedEntity {

    @Column(nullable = false, length = 60, updatable = false)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(name = "route_id")
    private UUID routeId;
    @Column(name = "carrier_id")
    private UUID carrierId;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_to")
    private LocalDate validTo;
    @Column(nullable = false)
    private boolean active = true;
    @Column(nullable = false)
    private int priority;
    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_method", nullable = false, length = 30)
    private FreightCalculationMethod calculationMethod;
    @Column(name = "fixed_amount", precision = 19, scale = 4)
    private BigDecimal fixedAmount;
    @Column(name = "unit_amount", precision = 19, scale = 6)
    private BigDecimal unitAmount;
    @Column(name = "minimum_charge", precision = 19, scale = 4)
    private BigDecimal minimumCharge;
    @Column(name = "maximum_charge", precision = 19, scale = 4)
    private BigDecimal maximumCharge;
    @Column(name = "minimum_weight_kg", precision = 19, scale = 3)
    private BigDecimal minimumWeightKg;
    @Column(name = "maximum_weight_kg", precision = 19, scale = 3)
    private BigDecimal maximumWeightKg;
    @Column(name = "minimum_volume_m3", precision = 19, scale = 6)
    private BigDecimal minimumVolumeM3;
    @Column(name = "maximum_volume_m3", precision = 19, scale = 6)
    private BigDecimal maximumVolumeM3;
    @Column(name = "minimum_distance_km", precision = 19, scale = 3)
    private BigDecimal minimumDistanceKm;
    @Column(name = "maximum_distance_km", precision = 19, scale = 3)
    private BigDecimal maximumDistanceKm;

    protected FreightRate() {
    }

    public FreightRate(UUID companyId, String code, String name, String currencyCode, LocalDate validFrom,
                       FreightCalculationMethod calculationMethod) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.currencyCode = currencyCode;
        this.validFrom = validFrom;
        this.calculationMethod = calculationMethod;
    }

    public void update(String name, UUID routeId, UUID carrierId, String currencyCode, LocalDate validFrom,
                       LocalDate validTo, boolean active, int priority,
                       FreightCalculationMethod calculationMethod, BigDecimal fixedAmount, BigDecimal unitAmount,
                       BigDecimal minimumCharge, BigDecimal maximumCharge, BigDecimal minimumWeightKg,
                       BigDecimal maximumWeightKg, BigDecimal minimumVolumeM3, BigDecimal maximumVolumeM3,
                       BigDecimal minimumDistanceKm, BigDecimal maximumDistanceKm) {
        this.name = name;
        this.routeId = routeId;
        this.carrierId = carrierId;
        this.currencyCode = currencyCode;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = active;
        this.priority = priority;
        this.calculationMethod = calculationMethod;
        this.fixedAmount = fixedAmount;
        this.unitAmount = unitAmount;
        this.minimumCharge = minimumCharge;
        this.maximumCharge = maximumCharge;
        this.minimumWeightKg = minimumWeightKg;
        this.maximumWeightKg = maximumWeightKg;
        this.minimumVolumeM3 = minimumVolumeM3;
        this.maximumVolumeM3 = maximumVolumeM3;
        this.minimumDistanceKm = minimumDistanceKm;
        this.maximumDistanceKm = maximumDistanceKm;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public UUID getRouteId() { return routeId; }
    public UUID getCarrierId() { return carrierId; }
    public String getCurrencyCode() { return currencyCode; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidTo() { return validTo; }
    public boolean isActive() { return active; }
    public int getPriority() { return priority; }
    public FreightCalculationMethod getCalculationMethod() { return calculationMethod; }
    public BigDecimal getFixedAmount() { return fixedAmount; }
    public BigDecimal getUnitAmount() { return unitAmount; }
    public BigDecimal getMinimumCharge() { return minimumCharge; }
    public BigDecimal getMaximumCharge() { return maximumCharge; }
    public BigDecimal getMinimumWeightKg() { return minimumWeightKg; }
    public BigDecimal getMaximumWeightKg() { return maximumWeightKg; }
    public BigDecimal getMinimumVolumeM3() { return minimumVolumeM3; }
    public BigDecimal getMaximumVolumeM3() { return maximumVolumeM3; }
    public BigDecimal getMinimumDistanceKm() { return minimumDistanceKm; }
    public BigDecimal getMaximumDistanceKm() { return maximumDistanceKm; }
}
