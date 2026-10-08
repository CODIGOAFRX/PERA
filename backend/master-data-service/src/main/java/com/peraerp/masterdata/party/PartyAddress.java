package com.peraerp.masterdata.party;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Dirección adicional de un tercero. La dirección fiscal sigue en la ficha; aquí van las de entrega (obras,
 * almacenes, tiendas). Solo una por tipo puede ser la principal.
 */
@Entity
@Table(name = "party_addresses")
public class PartyAddress extends CompanyScopedEntity {
    public static final String DELIVERY = "DELIVERY";

    @Column(name = "party_id", nullable = false)
    private UUID partyId;
    @Column(nullable = false, length = 30)
    private String type;
    @Column(length = 120)
    private String label;
    @Column(nullable = false, length = 200)
    private String line1;
    @Column(length = 200)
    private String line2;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(length = 100)
    private String city;
    @Column(length = 100)
    private String province;
    @Column(length = 100)
    private String country;
    @Column(name = "contact_phone", length = 40)
    private String contactPhone;
    @Column(nullable = false)
    private boolean primaryAddress;
    @Column(nullable = false)
    private boolean active = true;

    protected PartyAddress() {}

    public PartyAddress(UUID companyId, UUID partyId, String type) {
        super(companyId);
        this.partyId = partyId;
        this.type = type;
    }

    public void update(String label, String line1, String line2, String postalCode, String city, String province,
                       String country, String contactPhone, boolean primaryAddress, boolean active) {
        this.label = label;
        this.line1 = line1;
        this.line2 = line2;
        this.postalCode = postalCode;
        this.city = city;
        this.province = province;
        this.country = country;
        this.contactPhone = contactPhone;
        this.primaryAddress = primaryAddress;
        this.active = active;
    }

    public void clearPrimary() { this.primaryAddress = false; }

    public UUID getPartyId() { return partyId; }
    public String getType() { return type; }
    public String getLabel() { return label; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getPostalCode() { return postalCode; }
    public String getCity() { return city; }
    public String getProvince() { return province; }
    public String getCountry() { return country; }
    public String getContactPhone() { return contactPhone; }
    public boolean isPrimaryAddress() { return primaryAddress; }
    public boolean isActive() { return active; }
}
