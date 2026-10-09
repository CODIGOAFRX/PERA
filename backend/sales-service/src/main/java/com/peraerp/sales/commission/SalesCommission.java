package com.peraerp.sales.commission;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comisión de una factura para su comercial. Pendiente, se recalcula cuantas veces haga falta;
 * liquidada, queda fija hasta que se deshaga la liquidación.
 */
@Entity
@Table(name = "sales_commissions")
public class SalesCommission extends CompanyScopedEntity {
    public enum Status { PENDING, SETTLED }

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;
    @Column(name = "salesperson_id", nullable = false)
    private UUID salespersonId;
    @Column(name = "salesperson_name", nullable = false, length = 160)
    private String salespersonName;
    @Column(name = "base_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseAmount = BigDecimal.ZERO;
    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal commissionAmount = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;
    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;
    @Column(name = "settled_on")
    private LocalDate settledOn;
    @Column(name = "settlement_note", length = 300)
    private String settlementNote;
    @OneToMany(mappedBy = "commission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder ASC")
    private List<SalesCommissionLine> lines = new ArrayList<>();

    protected SalesCommission() {}

    public SalesCommission(UUID companyId, UUID documentId) {
        super(companyId);
        this.documentId = documentId;
    }

    /** Sustituye el cálculo anterior. Una comisión liquidada no se toca. */
    public void recalculate(UUID salespersonId, String salespersonName, List<SalesCommissionLine> newLines,
                            Instant calculatedAt) {
        if (status == Status.SETTLED) {
            throw new BusinessRuleException("La comisión ya está liquidada y no se recalcula.");
        }
        this.salespersonId = salespersonId;
        this.salespersonName = salespersonName;
        lines.clear();
        for (SalesCommissionLine line : newLines) {
            line.attachTo(this);
            lines.add(line);
        }
        this.baseAmount = newLines.stream().map(SalesCommissionLine::getBaseAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.commissionAmount = newLines.stream().map(SalesCommissionLine::getCommissionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.calculatedAt = calculatedAt;
    }

    public void settle(LocalDate settledOn, String note) {
        if (status == Status.SETTLED) {
            throw new BusinessRuleException("La comisión ya está liquidada.");
        }
        this.status = Status.SETTLED;
        this.settledOn = settledOn;
        this.settlementNote = note;
    }

    public void reopen() {
        if (status != Status.SETTLED) {
            throw new BusinessRuleException("La comisión no está liquidada.");
        }
        this.status = Status.PENDING;
        this.settledOn = null;
        this.settlementNote = null;
    }

    public boolean isSettled() { return status == Status.SETTLED; }
    public UUID getDocumentId() { return documentId; }
    public UUID getSalespersonId() { return salespersonId; }
    public String getSalespersonName() { return salespersonName; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public Status getStatus() { return status; }
    public Instant getCalculatedAt() { return calculatedAt; }
    public LocalDate getSettledOn() { return settledOn; }
    public String getSettlementNote() { return settlementNote; }
    public List<SalesCommissionLine> getLines() { return lines; }
}
