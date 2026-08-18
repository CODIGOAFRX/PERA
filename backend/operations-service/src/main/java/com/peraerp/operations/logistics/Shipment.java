package com.peraerp.operations.logistics;

import com.peraerp.operations.freight.FreightCalculationMethod;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shipments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_shipment_company_number", columnNames = {"company_id", "shipment_number"}),
        @UniqueConstraint(name = "uk_shipment_company_id", columnNames = {"company_id", "id"})
})
public class Shipment extends CompanyScopedEntity {

    @Column(name = "shipment_number", nullable = false, length = 80, updatable = false)
    private String shipmentNumber;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.PLANNED;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_exception", length = 20)
    private ShipmentStatus statusBeforeException;
    @Column(name = "origin_snapshot", length = 500)
    private String originSnapshot;
    @Column(name = "destination_snapshot", length = 500)
    private String destinationSnapshot;
    @Column(name = "carrier_id")
    private UUID carrierId;
    @Column(name = "vehicle_id")
    private UUID vehicleId;
    @Column(name = "route_id")
    private UUID routeId;
    @Column(name = "planned_departure_at")
    private Instant plannedDepartureAt;
    @Column(name = "planned_arrival_at")
    private Instant plannedArrivalAt;
    @Column(name = "actual_departure_at")
    private Instant actualDepartureAt;
    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "freight_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal freightCost = BigDecimal.ZERO;
    @Column(name = "freight_rate_id")
    private UUID freightRateId;
    @Column(name = "freight_rate_code_snapshot", length = 60)
    private String freightRateCodeSnapshot;
    @Column(name = "freight_rate_name_snapshot", length = 180)
    private String freightRateNameSnapshot;
    @Enumerated(EnumType.STRING)
    @Column(name = "freight_method_snapshot", length = 30)
    private FreightCalculationMethod freightMethodSnapshot;
    @Column(name = "freight_pricing_date_snapshot")
    private LocalDate freightPricingDateSnapshot;
    @Column(name = "freight_fixed_component_snapshot", precision = 19, scale = 4)
    private BigDecimal freightFixedComponentSnapshot;
    @Column(name = "freight_variable_component_snapshot", precision = 19, scale = 4)
    private BigDecimal freightVariableComponentSnapshot;
    @Column(name = "freight_distance_km_snapshot", precision = 19, scale = 3)
    private BigDecimal freightDistanceKmSnapshot;
    @Column(name = "freight_minimum_applied_snapshot")
    private Boolean freightMinimumAppliedSnapshot;
    @Column(name = "freight_maximum_applied_snapshot")
    private Boolean freightMaximumAppliedSnapshot;
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
    @Column(name = "total_weight_kg", precision = 19, scale = 3)
    private BigDecimal totalWeightKg;
    @Column(name = "total_volume_m3", precision = 19, scale = 6)
    private BigDecimal totalVolumeM3;
    @Column(name = "status_note", length = 1000)
    private String statusNote;

    protected Shipment() {
    }

    public Shipment(UUID companyId, String shipmentNumber, String currencyCode) {
        super(companyId);
        this.shipmentNumber = shipmentNumber;
        this.currencyCode = currencyCode;
    }

    public void updatePlan(String originSnapshot, String destinationSnapshot, UUID carrierId, UUID vehicleId,
                           UUID routeId, Instant plannedDepartureAt, Instant plannedArrivalAt,
                           BigDecimal freightCost, String currencyCode, BigDecimal totalWeightKg,
                           BigDecimal totalVolumeM3) {
        requireEditable();
        this.originSnapshot = originSnapshot;
        this.destinationSnapshot = destinationSnapshot;
        this.carrierId = carrierId;
        this.vehicleId = vehicleId;
        this.routeId = routeId;
        this.plannedDepartureAt = plannedDepartureAt;
        this.plannedArrivalAt = plannedArrivalAt;
        this.freightCost = freightCost;
        this.currencyCode = currencyCode;
        this.totalWeightKg = totalWeightKg;
        this.totalVolumeM3 = totalVolumeM3;
        clearFreightRateSnapshot();
    }

    public void applyFreightQuote(UUID rateId, String rateCode, String rateName,
                                  FreightCalculationMethod calculationMethod, LocalDate pricingDate,
                                  BigDecimal fixedComponent, BigDecimal variableComponent,
                                  BigDecimal distanceKm, boolean minimumApplied, boolean maximumApplied,
                                  BigDecimal amount, String quoteCurrencyCode) {
        requireEditable();
        if (!currencyCode.equals(quoteCurrencyCode)) {
            throw new IllegalStateException("La moneda de la tarifa no coincide con la moneda del envío.");
        }
        freightRateId = rateId;
        freightRateCodeSnapshot = rateCode;
        freightRateNameSnapshot = rateName;
        freightMethodSnapshot = calculationMethod;
        freightPricingDateSnapshot = pricingDate;
        freightFixedComponentSnapshot = fixedComponent;
        freightVariableComponentSnapshot = variableComponent;
        freightDistanceKmSnapshot = distanceKm;
        freightMinimumAppliedSnapshot = minimumApplied;
        freightMaximumAppliedSnapshot = maximumApplied;
        freightCost = amount;
    }

    private void clearFreightRateSnapshot() {
        freightRateId = null;
        freightRateCodeSnapshot = null;
        freightRateNameSnapshot = null;
        freightMethodSnapshot = null;
        freightPricingDateSnapshot = null;
        freightFixedComponentSnapshot = null;
        freightVariableComponentSnapshot = null;
        freightDistanceKmSnapshot = null;
        freightMinimumAppliedSnapshot = null;
        freightMaximumAppliedSnapshot = null;
    }

    public void startPacking() {
        transitionFrom(ShipmentStatus.PLANNED, ShipmentStatus.PACKING);
    }

    public void markReady() {
        transitionFrom(ShipmentStatus.PACKING, ShipmentStatus.READY);
    }

    public void dispatch(Instant departedAt) {
        transitionFrom(ShipmentStatus.READY, ShipmentStatus.DISPATCHED);
        this.actualDepartureAt = departedAt;
    }

    public void markInTransit() {
        transitionFrom(ShipmentStatus.DISPATCHED, ShipmentStatus.IN_TRANSIT);
    }

    public void arrive(Instant arrivedAt) {
        if (status != ShipmentStatus.DISPATCHED && status != ShipmentStatus.IN_TRANSIT) {
            throw invalidTransition(ShipmentStatus.ARRIVED);
        }
        if (actualDepartureAt != null && arrivedAt.isBefore(actualDepartureAt)) {
            throw new IllegalStateException("La llegada real no puede ser anterior a la salida real.");
        }
        status = ShipmentStatus.ARRIVED;
        actualArrivalAt = arrivedAt;
        statusNote = null;
    }

    public void deliver(Instant deliveredAt) {
        if (status != ShipmentStatus.ARRIVED) {
            throw invalidTransition(ShipmentStatus.DELIVERED);
        }
        if (actualArrivalAt != null && deliveredAt.isBefore(actualArrivalAt)) {
            throw new IllegalStateException("La entrega no puede ser anterior a la llegada real.");
        }
        status = ShipmentStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
        statusNote = null;
    }

    public void reportException(String reason) {
        if (status == ShipmentStatus.EXCEPTION || status == ShipmentStatus.DELIVERED || status == ShipmentStatus.CANCELLED) {
            throw invalidTransition(ShipmentStatus.EXCEPTION);
        }
        statusBeforeException = status;
        status = ShipmentStatus.EXCEPTION;
        statusNote = reason;
    }

    public void resolveException() {
        if (status != ShipmentStatus.EXCEPTION || statusBeforeException == null) {
            throw new IllegalStateException("El envío no tiene una excepción resoluble.");
        }
        status = statusBeforeException;
        statusBeforeException = null;
        statusNote = null;
    }

    public void cancel(String reason) {
        if (status != ShipmentStatus.PLANNED && status != ShipmentStatus.PACKING
                && status != ShipmentStatus.READY && status != ShipmentStatus.EXCEPTION) {
            throw invalidTransition(ShipmentStatus.CANCELLED);
        }
        status = ShipmentStatus.CANCELLED;
        statusBeforeException = null;
        statusNote = reason;
    }

    public boolean isPlanEditable() {
        return status == ShipmentStatus.PLANNED || status == ShipmentStatus.PACKING || status == ShipmentStatus.READY;
    }

    private void requireEditable() {
        if (!isPlanEditable()) {
            throw new IllegalStateException("El plan de un envío expedido no se puede modificar.");
        }
    }

    private void transitionFrom(ShipmentStatus expected, ShipmentStatus target) {
        if (status != expected) {
            throw invalidTransition(target);
        }
        status = target;
        statusNote = null;
    }

    private IllegalStateException invalidTransition(ShipmentStatus target) {
        return new IllegalStateException("No se puede pasar un envío de " + status + " a " + target + ".");
    }

    public String getShipmentNumber() { return shipmentNumber; }
    public ShipmentStatus getStatus() { return status; }
    public ShipmentStatus getStatusBeforeException() { return statusBeforeException; }
    public String getOriginSnapshot() { return originSnapshot; }
    public String getDestinationSnapshot() { return destinationSnapshot; }
    public UUID getCarrierId() { return carrierId; }
    public UUID getVehicleId() { return vehicleId; }
    public UUID getRouteId() { return routeId; }
    public Instant getPlannedDepartureAt() { return plannedDepartureAt; }
    public Instant getPlannedArrivalAt() { return plannedArrivalAt; }
    public Instant getActualDepartureAt() { return actualDepartureAt; }
    public Instant getActualArrivalAt() { return actualArrivalAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public BigDecimal getFreightCost() { return freightCost; }
    public UUID getFreightRateId() { return freightRateId; }
    public String getFreightRateCodeSnapshot() { return freightRateCodeSnapshot; }
    public String getFreightRateNameSnapshot() { return freightRateNameSnapshot; }
    public FreightCalculationMethod getFreightMethodSnapshot() { return freightMethodSnapshot; }
    public LocalDate getFreightPricingDateSnapshot() { return freightPricingDateSnapshot; }
    public BigDecimal getFreightFixedComponentSnapshot() { return freightFixedComponentSnapshot; }
    public BigDecimal getFreightVariableComponentSnapshot() { return freightVariableComponentSnapshot; }
    public BigDecimal getFreightDistanceKmSnapshot() { return freightDistanceKmSnapshot; }
    public Boolean getFreightMinimumAppliedSnapshot() { return freightMinimumAppliedSnapshot; }
    public Boolean getFreightMaximumAppliedSnapshot() { return freightMaximumAppliedSnapshot; }
    public String getCurrencyCode() { return currencyCode; }
    public BigDecimal getTotalWeightKg() { return totalWeightKg; }
    public BigDecimal getTotalVolumeM3() { return totalVolumeM3; }
    public String getStatusNote() { return statusNote; }
}
