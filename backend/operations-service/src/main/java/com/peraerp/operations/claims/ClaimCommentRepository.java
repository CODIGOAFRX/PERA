package com.peraerp.operations.claims;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimCommentRepository extends JpaRepository<ClaimComment, UUID> {

    List<ClaimComment> findAllByCompanyIdAndClaimIdOrderByCreatedAtAsc(UUID companyId, UUID claimId);

    boolean existsByCompanyIdAndClaimId(UUID companyId, UUID claimId);
}
