package com.peraerp.finance.cash;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "cash_registers", uniqueConstraints = @UniqueConstraint(
        name = "uk_cash_register_code", columnNames = {"company_id", "code"}))
public class CashRegister extends CompanyScopedEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "owner_name", length = 160)
    private String ownerName;
    @Column(nullable = false)
    private boolean active = true;

    protected CashRegister() {
    }

    public CashRegister(UUID companyId, String code, String name) {
        super(companyId);
        this.code = code;
        this.name = name;
    }

    public void update(String name, String ownerName, boolean active) {
        this.name = name;
        this.ownerName = ownerName;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getOwnerName() { return ownerName; }
    public boolean isActive() { return active; }
}
