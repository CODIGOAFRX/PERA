package com.peraerp.masterdata.party;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PartyAddressRepository extends JpaRepository<PartyAddress, UUID> {
    List<PartyAddress> findAllByCompanyIdAndPartyId(UUID companyId, UUID partyId);
}
