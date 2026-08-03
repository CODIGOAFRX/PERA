package com.peraerp.finance.risk;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="customer_risks",uniqueConstraints=@UniqueConstraint(name="uk_customer_risk",columnNames={"company_id","customer_id"}))
public class CustomerRisk extends CompanyScopedEntity{
    @Column(name="customer_id",nullable=false) private UUID customerId;
    @Column(name="current_exposure",nullable=false,precision=19,scale=4) private BigDecimal currentExposure=BigDecimal.ZERO;
    @Column(name="credit_limit",nullable=false,precision=19,scale=4) private BigDecimal creditLimit=BigDecimal.ZERO;
    @Column(name="warning_threshold",nullable=false,precision=19,scale=4) private BigDecimal warningThreshold=BigDecimal.ZERO;
    @Enumerated(EnumType.STRING) @Column(name="risk_action",nullable=false,length=30) private RiskAction action=RiskAction.WARN;
    protected CustomerRisk(){}
}
