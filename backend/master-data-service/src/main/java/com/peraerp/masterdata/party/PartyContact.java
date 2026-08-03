package com.peraerp.masterdata.party;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

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
    @Column(length = 180)
    private String email;
    @Column(nullable = false)
    private boolean primaryContact;

    protected PartyContact() {}
}
