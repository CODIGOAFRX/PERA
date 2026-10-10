package com.peraerp.operations.salesdelivery;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** Marca de lectura por empresa de los tickets del TPV: hasta qué modificación se ha procesado. */
@Entity
@Table(name = "pos_ticket_sync", uniqueConstraints = @UniqueConstraint(
        name = "uk_pos_ticket_sync_company", columnNames = "company_id"))
public class PosTicketSync extends CompanyScopedEntity {

    @Column(name = "last_source_update", nullable = false)
    private Instant lastSourceUpdate;

    protected PosTicketSync() {
    }

    public PosTicketSync(UUID companyId, Instant lastSourceUpdate) {
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
