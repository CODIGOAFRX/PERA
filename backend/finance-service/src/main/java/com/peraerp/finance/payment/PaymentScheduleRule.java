package com.peraerp.finance.payment;
import com.peraerp.platform.domain.AuditableEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
@Entity @Table(name="payment_schedule_rules",uniqueConstraints=@UniqueConstraint(name="uk_payment_rule_order",columnNames={"payment_method_id","installment_number"}))
public class PaymentScheduleRule extends AuditableEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_method_id",nullable=false)
    private PaymentMethod paymentMethod;
    @Column(name="installment_number",nullable=false) private int installmentNumber;
    @Column(name="due_days",nullable=false) private int dueDays;
    @Column(nullable=false,precision=9,scale=4) private BigDecimal percentage;
    protected PaymentScheduleRule() {}
    PaymentScheduleRule(PaymentMethod paymentMethod,int installmentNumber,int dueDays,BigDecimal percentage){this.paymentMethod=paymentMethod;this.installmentNumber=installmentNumber;this.dueDays=dueDays;this.percentage=percentage;}
    public int getInstallmentNumber(){return installmentNumber;} public int getDueDays(){return dueDays;} public BigDecimal getPercentage(){return percentage;}
}
