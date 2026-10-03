package com.peraerp.operations.claims;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Elemento de una tabla de clasificación de reclamaciones. Solo las acciones preventivas llevan
 * plazo de seguimiento: los días que hay para comprobar que la acción ha funcionado.
 */
@Entity
@Table(name = "claim_catalog_items", uniqueConstraints = @UniqueConstraint(
        name = "uk_claim_catalog_company_id", columnNames = {"company_id", "id"}))
public class ClaimCatalogItem extends CompanyScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private ClaimCatalogKind kind;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "follow_up_days")
    private Integer followUpDays;
    @Column(nullable = false)
    private boolean active = true;

    protected ClaimCatalogItem() {
    }

    public ClaimCatalogItem(UUID companyId, ClaimCatalogKind kind) {
        super(companyId);
        this.kind = kind;
    }

    public void update(String name, Integer followUpDays, boolean active) {
        if (followUpDays != null && kind != ClaimCatalogKind.PREVENTIVE_ACTION) {
            throw new IllegalStateException("Solo las acciones preventivas tienen plazo de seguimiento.");
        }
        this.name = name;
        this.followUpDays = followUpDays;
        this.active = active;
    }

    public ClaimCatalogKind getKind() { return kind; }
    public String getName() { return name; }
    public Integer getFollowUpDays() { return followUpDays; }
    public boolean isActive() { return active; }
}
