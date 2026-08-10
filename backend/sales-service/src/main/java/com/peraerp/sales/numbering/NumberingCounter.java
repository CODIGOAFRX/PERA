package com.peraerp.sales.numbering;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "numbering_counters", uniqueConstraints =
        @UniqueConstraint(name = "uk_numbering_counter", columnNames = {"company_id", "scheme_id", "period_key"}))
public class NumberingCounter extends CompanyScopedEntity {

    @Column(name = "scheme_id", nullable = false, updatable = false)
    private UUID schemeId;

    @Column(name = "period_key", nullable = false, length = 20, updatable = false)
    private String periodKey;

    @Column(name = "next_value", nullable = false)
    private long nextValue;

    protected NumberingCounter() {
    }

    public NumberingCounter(UUID companyId, UUID schemeId, String periodKey, long initialValue) {
        super(companyId);
        this.schemeId = schemeId;
        this.periodKey = periodKey;
        this.nextValue = initialValue;
    }

    public long takeNext() {
        return nextValue++;
    }

    public UUID getSchemeId() { return schemeId; }
    public String getPeriodKey() { return periodKey; }
    public long getNextValue() { return nextValue; }
}
