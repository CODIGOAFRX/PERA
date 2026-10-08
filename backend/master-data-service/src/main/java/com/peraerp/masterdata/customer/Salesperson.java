package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Comercial que lleva clientes. El porcentaje es la comisión por defecto; el cálculo de comisiones sobre
 * las ventas llegará con el bloque de comisiones.
 */
@Entity
@Table(name = "salespeople", uniqueConstraints = @UniqueConstraint(
        name = "uk_salesperson_company_code", columnNames = {"company_id", "code"}))
public class Salesperson extends CompanyScopedEntity {
    @Column(nullable = false, length = 20)
    private String code;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 180)
    private String email;
    @Column(length = 40)
    private String phone;
    @Column(name = "commission_percentage", precision = 7, scale = 4)
    private BigDecimal commissionPercentage;
    @Column(nullable = false)
    private boolean active = true;

    protected Salesperson() {}

    public Salesperson(UUID companyId, String code, String name, String email, String phone,
                       BigDecimal commissionPercentage, boolean active) {
        super(companyId);
        this.code = code;
        update(name, email, phone, commissionPercentage, active);
    }

    public void update(String name, String email, String phone, BigDecimal commissionPercentage, boolean active) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.commissionPercentage = commissionPercentage;
        this.active = active;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public BigDecimal getCommissionPercentage() { return commissionPercentage; }
    public boolean isActive() { return active; }
}
