package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "price_lists", uniqueConstraints = @UniqueConstraint(name = "uk_price_list_code", columnNames = {"company_id", "code"}))
public class PriceList extends CompanyScopedEntity {
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false, length = 3)
    private String currency = "EUR";
    @Column(nullable = false)
    private boolean active = true;
    protected PriceList() {}
    public PriceList(UUID companyId, String code, String name, String currency) { super(companyId); this.code=code; this.name=name; this.currency=currency; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
}
