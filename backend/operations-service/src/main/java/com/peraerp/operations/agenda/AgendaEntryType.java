package com.peraerp.operations.agenda;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Tipo de cita configurable por la empresa: medición, montaje, visita comercial, llamada... */
@Entity
@Table(name = "agenda_entry_types", uniqueConstraints = @UniqueConstraint(
        name = "uk_agenda_type_company_id", columnNames = {"company_id", "id"}))
public class AgendaEntryType extends CompanyScopedEntity {

    @Column(nullable = false, length = 80)
    private String name;
    @Column(length = 7)
    private String color;
    @Column(nullable = false)
    private boolean active = true;

    protected AgendaEntryType() {
    }

    public AgendaEntryType(UUID companyId) {
        super(companyId);
    }

    public void update(String name, String color, boolean active) {
        this.name = name;
        this.color = color;
        this.active = active;
    }

    public String getName() { return name; }
    public String getColor() { return color; }
    public boolean isActive() { return active; }
}
