package com.peraerp.operations.inventory;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "warehouses", uniqueConstraints = {
        @UniqueConstraint(name = "uk_warehouse_company_code", columnNames = {"company_id", "code"}),
        @UniqueConstraint(name = "uk_warehouse_company_id", columnNames = {"company_id", "id"})
})
public class Warehouse extends CompanyScopedEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 500)
    private String location;
    @Column(name = "default_warehouse", nullable = false)
    private boolean defaultWarehouse;
    @Column(nullable = false)
    private boolean active = true;

    protected Warehouse() {
    }

    public Warehouse(UUID companyId, String code, String name) {
        super(companyId);
        this.code = code;
        this.name = name;
    }

    public void update(String name, String location, boolean defaultWarehouse, boolean active) {
        if (defaultWarehouse && !active) {
            throw new IllegalStateException("El almacén predeterminado debe estar activo.");
        }
        this.name = name;
        this.location = location;
        this.defaultWarehouse = defaultWarehouse;
        this.active = active;
    }

    public void clearDefault() {
        this.defaultWarehouse = false;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public boolean isDefaultWarehouse() { return defaultWarehouse; }
    public boolean isActive() { return active; }
}
