package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** Elemento de una tabla de clasificación de clientes: grupo, tipo, forma de entrega o motivo de baja. */
@Entity
@Table(name = "customer_catalog_items")
public class CustomerCatalogItem extends CompanyScopedEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CustomerCatalogKind kind;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false)
    private boolean active = true;

    protected CustomerCatalogItem() {}

    public CustomerCatalogItem(UUID companyId, CustomerCatalogKind kind, String name, boolean active) {
        super(companyId);
        this.kind = kind;
        this.name = name;
        this.active = active;
    }

    public void update(String name, boolean active) {
        this.name = name;
        this.active = active;
    }

    public CustomerCatalogKind getKind() { return kind; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
