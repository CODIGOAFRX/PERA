package com.peraerp.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

@MappedSuperclass
public abstract class CompanyScopedEntity extends AuditableEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    protected CompanyScopedEntity() {
    }

    protected CompanyScopedEntity(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getCompanyId() {
        return companyId;
    }
}
