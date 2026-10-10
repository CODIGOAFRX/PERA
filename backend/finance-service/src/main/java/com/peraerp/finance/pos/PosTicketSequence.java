package com.peraerp.finance.pos;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Contador de tickets de una caja por tipo y año. */
@Entity
@Table(name = "pos_ticket_sequences")
public class PosTicketSequence extends CompanyScopedEntity {

    @Column(name = "cash_register_id", nullable = false, updatable = false)
    private UUID cashRegisterId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PosTicketKind kind;
    @Column(name = "fiscal_year", nullable = false, updatable = false)
    private int fiscalYear;
    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected PosTicketSequence() {
    }

    public PosTicketSequence(UUID companyId, UUID cashRegisterId, PosTicketKind kind, int fiscalYear) {
        super(companyId);
        this.cashRegisterId = cashRegisterId;
        this.kind = kind;
        this.fiscalYear = fiscalYear;
    }

    public long next() {
        return ++lastValue;
    }
}
