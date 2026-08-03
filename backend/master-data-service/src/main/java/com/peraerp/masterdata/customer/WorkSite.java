package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "work_sites", uniqueConstraints = @UniqueConstraint(name = "uk_work_site_code", columnNames = {"company_id", "code"}))
public class WorkSite extends CompanyScopedEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(length = 180)
    private String builder;
    @Column(columnDefinition = "text")
    private String address;
    @Column(columnDefinition = "text")
    private String description;
    @Column(nullable = false)
    private boolean active = true;

    protected WorkSite() {}
}
