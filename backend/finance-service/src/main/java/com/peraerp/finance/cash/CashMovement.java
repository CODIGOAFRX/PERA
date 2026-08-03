package com.peraerp.finance.cash;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="cash_movements")
public class CashMovement extends CompanyScopedEntity{
    @Column(name="cash_session_id",nullable=false) private UUID cashSessionId;
    @Column(name="occurred_at",nullable=false) private Instant occurredAt;
    @Enumerated(EnumType.STRING) @Column(name="movement_type",nullable=false,length=40) private CashMovementType type;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="document_id") private UUID documentId;
    @Column(name="receipt_id") private UUID receiptId;
    @Column(nullable=false,length=300) private String concept;
    protected CashMovement(){}
}
