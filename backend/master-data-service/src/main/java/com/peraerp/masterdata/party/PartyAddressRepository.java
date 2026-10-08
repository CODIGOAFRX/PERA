package com.peraerp.masterdata.party;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyAddressRepository extends JpaRepository<PartyAddress, UUID> {
    List<PartyAddress> findAllByCompanyIdAndPartyId(UUID companyId, UUID partyId);

    List<PartyAddress> findAllByCompanyIdAndPartyIdAndTypeOrderByPrimaryAddressDescActiveDescLabelAsc(
            UUID companyId, UUID partyId, String type);

    Optional<PartyAddress> findByIdAndCompanyIdAndPartyId(UUID id, UUID companyId, UUID partyId);
}
