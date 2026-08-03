package com.peraerp.finance.cash;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
@Entity @Table(name="cash_registers",uniqueConstraints=@UniqueConstraint(name="uk_cash_register_code",columnNames={"company_id","code"}))
public class CashRegister extends CompanyScopedEntity{
    @Column(nullable=false,length=40) private String code;
    @Column(nullable=false,length=160) private String name;
    @Column(name="owner_name",length=160) private String ownerName;
    @Column(nullable=false) private boolean active=true;
    protected CashRegister(){}
}
