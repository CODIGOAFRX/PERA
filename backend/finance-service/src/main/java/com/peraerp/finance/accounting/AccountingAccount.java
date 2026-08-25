package com.peraerp.finance.accounting;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "accounting_accounts", uniqueConstraints =
        @UniqueConstraint(name = "uk_accounting_account_code", columnNames = {"company_id", "code"}))
public class AccountingAccount extends CompanyScopedEntity {
    @Column(nullable = false, length = 20, updatable = false) private String code;
    @Column(nullable = false, length = 180) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "account_kind", nullable = false, length = 30)
    private AccountKind kind;
    @Column(name = "system_defined", nullable = false) private boolean systemDefined;
    @Column(nullable = false) private boolean active = true;

    protected AccountingAccount() {}

    public AccountingAccount(UUID companyId, String code, String name, AccountKind kind, boolean systemDefined) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.systemDefined = systemDefined;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public AccountKind getKind() { return kind; }
    public boolean isSystemDefined() { return systemDefined; }
    public boolean isActive() { return active; }
}
