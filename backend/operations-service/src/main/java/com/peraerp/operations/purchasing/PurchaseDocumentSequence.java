package com.peraerp.operations.purchasing;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "purchase_document_sequences", uniqueConstraints = @UniqueConstraint(
        name = "uk_purchase_sequence", columnNames = {"company_id", "document_type", "fiscal_year"}))
public class PurchaseDocumentSequence extends CompanyScopedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private PurchaseDocumentType type;
    @Column(name = "fiscal_year", nullable = false, updatable = false)
    private int fiscalYear;
    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected PurchaseDocumentSequence() {
    }

    public PurchaseDocumentSequence(UUID companyId, PurchaseDocumentType type, int fiscalYear) {
        super(companyId);
        this.type = type;
        this.fiscalYear = fiscalYear;
    }

    public long next() {
        return ++lastValue;
    }

    public PurchaseDocumentType getType() { return type; }
    public int getFiscalYear() { return fiscalYear; }
    public long getLastValue() { return lastValue; }
}
