package com.peraerp.masterdata.party;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Persona de contacto de un tercero (compras, administración, obra...). Solo una puede ser la principal. */
@Entity
@Table(name = "party_contacts")
public class PartyContact extends CompanyScopedEntity {
    @Column(name = "party_id", nullable = false)
    private UUID partyId;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 120)
    private String position;
    @Column(length = 40)
    private String phone;
    @Column(length = 40)
    private String mobile;
    @Column(length = 180)
    private String email;
    @Column(length = 300)
    private String notes;
    @Column(nullable = false)
    private boolean primaryContact;

    protected PartyContact() {}

    public PartyContact(UUID companyId, UUID partyId) {
        super(companyId);
        this.partyId = partyId;
    }

    public void update(String name, String position, String phone, String mobile, String email, String notes,
                       boolean primaryContact) {
        this.name = name;
        this.position = position;
        this.phone = phone;
        this.mobile = mobile;
        this.email = email;
        this.notes = notes;
        this.primaryContact = primaryContact;
    }

    public void clearPrimary() { this.primaryContact = false; }

    public UUID getPartyId() { return partyId; }
    public String getName() { return name; }
    public String getPosition() { return position; }
    public String getPhone() { return phone; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getNotes() { return notes; }
    public boolean isPrimaryContact() { return primaryContact; }
}
