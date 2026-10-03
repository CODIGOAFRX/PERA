package com.peraerp.operations.claims;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "claim_sequences", uniqueConstraints = @UniqueConstraint(
        name = "uk_claim_sequence", columnNames = {"company_id", "fiscal_year"}))
public class ClaimSequence extends CompanyScopedEntity {

    @Column(name = "fiscal_year", nullable = false, updatable = false)
    private int fiscalYear;
    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected ClaimSequence() {
    }

    public ClaimSequence(UUID companyId, int fiscalYear) {
        super(companyId);
        this.fiscalYear = fiscalYear;
    }

    public long next() {
        return ++lastValue;
    }

    public int getFiscalYear() { return fiscalYear; }
}
