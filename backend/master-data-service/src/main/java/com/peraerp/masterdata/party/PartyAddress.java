package com.peraerp.masterdata.party;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "party_addresses")
public class PartyAddress extends CompanyScopedEntity {
    @Column(name = "party_id", nullable = false)
    private UUID partyId;
    @Column(nullable = false, length = 30)
    private String type;
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
    @Column(nullable = false)
    private boolean primaryAddress;

    protected PartyAddress() {}
}
