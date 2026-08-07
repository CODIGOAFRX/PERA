package com.peraerp.masterdata.supplier;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "supplier_profiles", uniqueConstraints = @UniqueConstraint(name = "uk_supplier_party", columnNames = "party_id"))
public class SupplierProfile extends CompanyScopedEntity {
    @Column(name = "party_id", nullable = false)
    private UUID partyId;
    @Column(length = 160)
    private String carrier;
    @Column(length = 160)
    private String route;
    @Column(name = "default_payment_method_id")
    private UUID defaultPaymentMethodId;

    protected SupplierProfile() {}

    public SupplierProfile(UUID companyId, UUID partyId, String carrier, String route, UUID defaultPaymentMethodId) {
        super(companyId);
        this.partyId = partyId;
        this.carrier = carrier;
        this.route = route;
        this.defaultPaymentMethodId = defaultPaymentMethodId;
    }

    public void update(String carrier, String route, UUID defaultPaymentMethodId) {
        this.carrier = carrier;
        this.route = route;
        this.defaultPaymentMethodId = defaultPaymentMethodId;
    }

    public UUID getPartyId() { return partyId; }
    public String getCarrier() { return carrier; }
    public String getRoute() { return route; }
    public UUID getDefaultPaymentMethodId() { return defaultPaymentMethodId; }
}
