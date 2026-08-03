package com.peraerp.finance.remittance;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
@Entity @Table(name="remittances",uniqueConstraints=@UniqueConstraint(name="uk_remittance_number",columnNames={"company_id","remittance_number"}))
public class Remittance extends CompanyScopedEntity{
    @Column(name="remittance_number",nullable=false,length=50) private String remittanceNumber;
    @Column(name="bank_account",nullable=false,length=80) private String bankAccount;
    @Column(name="creation_date",nullable=false) private LocalDate creationDate;
    @Column(name="sent_date") private LocalDate sentDate;
    @Column(name="settlement_date") private LocalDate settlementDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private RemittanceStatus status=RemittanceStatus.DRAFT;
    @Column(name="total_amount",nullable=false,precision=19,scale=4) private BigDecimal totalAmount=BigDecimal.ZERO;
    protected Remittance(){}
}
