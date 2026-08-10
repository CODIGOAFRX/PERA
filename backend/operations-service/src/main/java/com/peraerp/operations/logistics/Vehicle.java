package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "vehicles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_vehicle_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_vehicle_company_plate", columnNames = {"company_id", "registration_plate"}),
        @UniqueConstraint(name = "uk_vehicle_company_id", columnNames = {"company_id", "id"})
})
public class Vehicle extends CompanyScopedEntity {

    @Column(nullable = false, length = 60, updatable = false)
    private String code;
    @Column(name = "registration_plate", length = 30)
    private String registrationPlate;
    @Column(name = "vehicle_type", nullable = false, length = 80)
    private String vehicleType;
    @Column(name = "carrier_id")
    private UUID carrierId;
    @Column(name = "capacity_weight_kg", precision = 19, scale = 3)
    private BigDecimal capacityWeightKg;
    @Column(name = "capacity_volume_m3", precision = 19, scale = 6)
    private BigDecimal capacityVolumeM3;
    @Column(nullable = false)
    private boolean active = true;

    protected Vehicle() {
    }

    public Vehicle(UUID companyId, String code, String registrationPlate, String vehicleType) {
        super(companyId);
        this.code = code;
        this.registrationPlate = registrationPlate;
        this.vehicleType = vehicleType;
    }

    public void update(String registrationPlate, String vehicleType, UUID carrierId,
                       BigDecimal capacityWeightKg, BigDecimal capacityVolumeM3, boolean active) {
        this.registrationPlate = registrationPlate;
        this.vehicleType = vehicleType;
        this.carrierId = carrierId;
        this.capacityWeightKg = capacityWeightKg;
        this.capacityVolumeM3 = capacityVolumeM3;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getRegistrationPlate() { return registrationPlate; }
    public String getVehicleType() { return vehicleType; }
    public UUID getCarrierId() { return carrierId; }
    public BigDecimal getCapacityWeightKg() { return capacityWeightKg; }
    public BigDecimal getCapacityVolumeM3() { return capacityVolumeM3; }
    public boolean isActive() { return active; }
}
