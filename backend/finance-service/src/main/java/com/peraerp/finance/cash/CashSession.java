package com.peraerp.finance.cash;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Turno de una caja: desde que se abre con un fondo hasta que se arquea y se cierra. */
@Entity
@Table(name = "cash_sessions")
public class CashSession extends CompanyScopedEntity {

    @Column(name = "cash_register_id", nullable = false, updatable = false)
    private UUID cashRegisterId;
    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;
    @Column(name = "closed_by")
    private UUID closedBy;
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "opening_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal openingAmount;
    @Column(name = "expected_closing_amount", precision = 19, scale = 4)
    private BigDecimal expectedClosingAmount;
    @Column(name = "actual_closing_amount", precision = 19, scale = 4)
    private BigDecimal actualClosingAmount;
    @Column(name = "closing_note", length = 300)
    private String closingNote;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashSessionStatus status = CashSessionStatus.OPEN;

    protected CashSession() {
    }

    public CashSession(UUID companyId, UUID cashRegisterId, UUID openedBy, Instant openedAt,
                       BigDecimal openingAmount) {
        super(companyId);
        this.cashRegisterId = cashRegisterId;
        this.openedBy = openedBy;
        this.openedAt = openedAt;
        this.openingAmount = openingAmount;
    }

    /**
     * Cierra el turno con el arqueo. La diferencia entre lo esperado y lo contado no se corrige:
     * queda a la vista para que alguien la explique.
     */
    public void close(UUID closedBy, Instant closedAt, BigDecimal expectedAmount, BigDecimal countedAmount,
                      String note) {
        requireOpen();
        this.status = CashSessionStatus.CLOSED;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
        this.expectedClosingAmount = expectedAmount;
        this.actualClosingAmount = countedAmount;
        this.closingNote = note;
    }

    public void requireOpen() {
        if (status != CashSessionStatus.OPEN) {
            throw new IllegalStateException("La sesión de caja ya está cerrada.");
        }
    }

    public UUID getCashRegisterId() { return cashRegisterId; }
    public UUID getOpenedBy() { return openedBy; }
    public UUID getClosedBy() { return closedBy; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public BigDecimal getOpeningAmount() { return openingAmount; }
    public BigDecimal getExpectedClosingAmount() { return expectedClosingAmount; }
    public BigDecimal getActualClosingAmount() { return actualClosingAmount; }
    public String getClosingNote() { return closingNote; }
    public CashSessionStatus getStatus() { return status; }
}
