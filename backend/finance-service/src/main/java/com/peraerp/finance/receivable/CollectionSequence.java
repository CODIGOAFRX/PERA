package com.peraerp.finance.receivable;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Contador anual por empresa para numerar recibos y remesas. */
@Entity
@Table(name = "collection_sequences", uniqueConstraints = @UniqueConstraint(
        name = "uk_collection_sequence", columnNames = {"company_id", "sequence_name", "fiscal_year"}))
public class CollectionSequence extends CompanyScopedEntity {

    public static final String RECEIPT = "RECEIPT";
    public static final String REMITTANCE = "REMITTANCE";

    @Column(name = "sequence_name", nullable = false, length = 30, updatable = false)
    private String name;
    @Column(name = "fiscal_year", nullable = false, updatable = false)
    private int fiscalYear;
    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected CollectionSequence() {
    }

    public CollectionSequence(UUID companyId, String name, int fiscalYear) {
        super(companyId);
        this.name = name;
        this.fiscalYear = fiscalYear;
    }

    public long next() {
        return ++lastValue;
    }

    public String getName() { return name; }
    public int getFiscalYear() { return fiscalYear; }
}
