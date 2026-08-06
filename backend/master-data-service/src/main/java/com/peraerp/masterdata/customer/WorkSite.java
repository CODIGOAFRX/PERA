package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Modelo heredado de obra de cliente.
 *
 * @deprecated Esta extensión orientada a construcción queda congelada mientras PERA evoluciona como ERP
 * horizontal. Se conserva para no perder compatibilidad ni datos, pero el núcleo y las nuevas funcionalidades
 * no deben depender de ella. Una futura capacidad transversal se modelará como proyecto o ubicación de servicio.
 */
@Entity
@Table(name = "work_sites", uniqueConstraints = @UniqueConstraint(name = "uk_work_site_code", columnNames = {"company_id", "code"}))
@Deprecated(since = "0.2", forRemoval = false)
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
