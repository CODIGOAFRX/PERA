package com.peraerp.operations.salesdelivery;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Marca de lectura por empresa: hasta qué modificación de Ventas se ha procesado. */
@Entity
@Table(name = "sales_delivery_sync", uniqueConstraints = @UniqueConstraint(
        name = "uk_sales_delivery_sync_company", columnNames = "company_id"))
public class SalesDeliverySync extends CompanyScopedEntity {

    @Column(name = "last_source_update", nullable = false)
    private Instant lastSourceUpdate;

    protected SalesDeliverySync() {
    }

    public SalesDeliverySync(UUID companyId, Instant lastSourceUpdate) {
        super(companyId);
        this.lastSourceUpdate = lastSourceUpdate;
    }

    public void advanceTo(Instant sourceUpdate) {
        if (sourceUpdate.isAfter(lastSourceUpdate)) {
            this.lastSourceUpdate = sourceUpdate;
        }
    }

    public Instant getLastSourceUpdate() { return lastSourceUpdate; }
}
