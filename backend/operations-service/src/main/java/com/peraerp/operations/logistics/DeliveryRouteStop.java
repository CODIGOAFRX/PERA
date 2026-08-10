package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_route_stops", uniqueConstraints = @UniqueConstraint(
        name = "uk_delivery_route_stop_sequence", columnNames = {"company_id", "route_id", "stop_sequence"}))
public class DeliveryRouteStop extends CompanyScopedEntity {

    @Column(name = "route_id", nullable = false, updatable = false)
    private UUID routeId;
    @Column(name = "stop_sequence", nullable = false)
    private int stopSequence;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(name = "location_snapshot", nullable = false, length = 500)
    private String locationSnapshot;
    @Column(name = "window_start")
    private Instant windowStart;
    @Column(name = "window_end")
    private Instant windowEnd;
    @Column(length = 1000)
    private String instructions;

    protected DeliveryRouteStop() {
    }

    public DeliveryRouteStop(UUID companyId, UUID routeId, int stopSequence, String name, String locationSnapshot,
                             Instant windowStart, Instant windowEnd, String instructions) {
        super(companyId);
        this.routeId = routeId;
        this.stopSequence = stopSequence;
        this.name = name;
        this.locationSnapshot = locationSnapshot;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.instructions = instructions;
    }

    public UUID getRouteId() { return routeId; }
    public int getStopSequence() { return stopSequence; }
    public String getName() { return name; }
    public String getLocationSnapshot() { return locationSnapshot; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public String getInstructions() { return instructions; }
}
