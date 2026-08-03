package com.peraerp.finance.payment;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name="payment_methods",uniqueConstraints=@UniqueConstraint(name="uk_payment_method_code",columnNames={"company_id","code"}))
public class PaymentMethod extends CompanyScopedEntity {
    @Column(nullable=false,length=40) private String code;
    @Column(nullable=false,length=160) private String name;
    @Column(nullable=false) private boolean active=true;
    @OneToMany(mappedBy="paymentMethod",cascade=CascadeType.ALL,orphanRemoval=true)
    @OrderBy("installmentNumber ASC")
    private List<PaymentScheduleRule> rules=new ArrayList<>();
    protected PaymentMethod() {}
    public PaymentMethod(UUID companyId,String code,String name){super(companyId);this.code=code;this.name=name;}
    public void addRule(int installmentNumber,int dueDays,java.math.BigDecimal percentage){rules.add(new PaymentScheduleRule(this,installmentNumber,dueDays,percentage));}
    public String getCode(){return code;} public String getName(){return name;} public boolean isActive(){return active;}
    public List<PaymentScheduleRule> getRules(){return List.copyOf(rules);}
}
