package com.peraerp.finance.ledger;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
@Entity @Table(name="financial_movements")
public class FinancialMovement extends CompanyScopedEntity{
    @Column(name="movement_date",nullable=false) private LocalDate movementDate;
    @Enumerated(EnumType.STRING) @Column(name="movement_type",nullable=false,length=40) private FinancialMovementType type;
    @Column(name="customer_id") private UUID customerId;
    @Column(name="document_id") private UUID documentId;
    @Column(name="receipt_id") private UUID receiptId;
    @Column(name="debit_amount",nullable=false,precision=19,scale=4) private BigDecimal debitAmount=BigDecimal.ZERO;
    @Column(name="credit_amount",nullable=false,precision=19,scale=4) private BigDecimal creditAmount=BigDecimal.ZERO;
    @Column(nullable=false,length=300) private String concept;
    protected FinancialMovement(){}
}
