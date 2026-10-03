package com.peraerp.operations.claims;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimLineRepository extends JpaRepository<ClaimLine, UUID> {

    List<ClaimLine> findAllByCompanyIdAndClaimIdOrderByLineSequenceAsc(UUID companyId, UUID claimId);

    void deleteAllByCompanyIdAndClaimId(UUID companyId, UUID claimId);
}
