package com.peraerp.masterdata.party;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PartyContactRepository extends JpaRepository<PartyContact, UUID> {
    List<PartyContact> findAllByCompanyIdAndPartyId(UUID companyId, UUID partyId);
}
