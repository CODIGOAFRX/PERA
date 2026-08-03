package com.peraerp.finance.cash;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="cash_sessions")
public class CashSession extends CompanyScopedEntity{
    @Column(name="cash_register_id",nullable=false) private UUID cashRegisterId;
    @Column(name="opened_by",nullable=false) private UUID openedBy;
    @Column(name="closed_by") private UUID closedBy;
    @Column(name="opened_at",nullable=false) private Instant openedAt;
    @Column(name="closed_at") private Instant closedAt;
    @Column(name="opening_amount",nullable=false,precision=19,scale=4) private BigDecimal openingAmount;
    @Column(name="expected_closing_amount",precision=19,scale=4) private BigDecimal expectedClosingAmount;
    @Column(name="actual_closing_amount",precision=19,scale=4) private BigDecimal actualClosingAmount;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private CashSessionStatus status=CashSessionStatus.OPEN;
    protected CashSession(){}
}
