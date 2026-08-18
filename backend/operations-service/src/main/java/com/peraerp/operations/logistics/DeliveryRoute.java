package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_routes", uniqueConstraints = {
        @UniqueConstraint(name = "uk_delivery_route_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_delivery_route_company_id", columnNames = {"company_id", "id"})
})
public class DeliveryRoute extends CompanyScopedEntity {

    @Column(nullable = false, length = 60, updatable = false)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(name = "origin_snapshot", nullable = false, length = 500)
    private String originSnapshot;
    @Column(name = "destination_snapshot", nullable = false, length = 500)
    private String destinationSnapshot;
    @Column(name = "distance_km", precision = 19, scale = 3)
    private BigDecimal distanceKm;
    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;
    @Column(name = "carrier_id")
    private UUID carrierId;
    @Column(name = "vehicle_id")
    private UUID vehicleId;
    @Column(name = "planned_departure_at")
    private Instant plannedDepartureAt;
    @Column(name = "planned_arrival_at")
    private Instant plannedArrivalAt;
    @Column(name = "delivery_window_start")
    private Instant deliveryWindowStart;
    @Column(name = "delivery_window_end")
    private Instant deliveryWindowEnd;
    @Column(nullable = false)
    private boolean active = true;

    protected DeliveryRoute() {
    }

    public DeliveryRoute(UUID companyId, String code, String name, String originSnapshot,
                         String destinationSnapshot) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.originSnapshot = originSnapshot;
        this.destinationSnapshot = destinationSnapshot;
    }

    public void update(String name, String originSnapshot, String destinationSnapshot, BigDecimal distanceKm,
                       Integer estimatedDurationMinutes, UUID carrierId, UUID vehicleId,
                       Instant plannedDepartureAt, Instant plannedArrivalAt, Instant deliveryWindowStart,
                       Instant deliveryWindowEnd, boolean active) {
        this.name = name;
        this.originSnapshot = originSnapshot;
        this.destinationSnapshot = destinationSnapshot;
        this.distanceKm = distanceKm;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.carrierId = carrierId;
        this.vehicleId = vehicleId;
        this.plannedDepartureAt = plannedDepartureAt;
        this.plannedArrivalAt = plannedArrivalAt;
        this.deliveryWindowStart = deliveryWindowStart;
        this.deliveryWindowEnd = deliveryWindowEnd;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getOriginSnapshot() { return originSnapshot; }
    public String getDestinationSnapshot() { return destinationSnapshot; }
    public BigDecimal getDistanceKm() { return distanceKm; }
    public Integer getEstimatedDurationMinutes() { return estimatedDurationMinutes; }
    public UUID getCarrierId() { return carrierId; }
    public UUID getVehicleId() { return vehicleId; }
    public Instant getPlannedDepartureAt() { return plannedDepartureAt; }
    public Instant getPlannedArrivalAt() { return plannedArrivalAt; }
    public Instant getDeliveryWindowStart() { return deliveryWindowStart; }
    public Instant getDeliveryWindowEnd() { return deliveryWindowEnd; }
    public boolean isActive() { return active; }
}
