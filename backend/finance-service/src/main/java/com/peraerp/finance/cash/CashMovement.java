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

/** Apunte del diario de caja. Es inmutable. */
@Entity
@Table(name = "cash_movements")
public class CashMovement extends CompanyScopedEntity {

    @Column(name = "cash_session_id", nullable = false, updatable = false)
    private UUID cashSessionId;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40, updatable = false)
    private CashMovementType type;
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;
    @Column(name = "document_id", updatable = false)
    private UUID documentId;
    @Column(name = "receipt_id", updatable = false)
    private UUID receiptId;
    @Column(nullable = false, length = 300, updatable = false)
    private String concept;

    protected CashMovement() {
    }

    public CashMovement(UUID companyId, UUID cashSessionId, Instant occurredAt, CashMovementType type,
                        BigDecimal amount, UUID documentId, UUID receiptId, String concept) {
        super(companyId);
        this.cashSessionId = cashSessionId;
        this.occurredAt = occurredAt;
        this.type = type;
        this.amount = amount;
        this.documentId = documentId;
        this.receiptId = receiptId;
        this.concept = concept;
    }

    /** Importe con signo: positivo si entra dinero en la caja, negativo si sale. */
    public BigDecimal signedAmount() {
        return type.isInflow() ? amount : amount.negate();
    }

    public UUID getCashSessionId() { return cashSessionId; }
    public Instant getOccurredAt() { return occurredAt; }
    public CashMovementType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public UUID getDocumentId() { return documentId; }
    public UUID getReceiptId() { return receiptId; }
    public String getConcept() { return concept; }
}
