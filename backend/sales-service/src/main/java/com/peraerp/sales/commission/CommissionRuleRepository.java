package com.peraerp.sales.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {
    Optional<CommissionRule> findByIdAndCompanyId(UUID id, UUID companyId);

    List<CommissionRule> findAllByCompanyIdAndSalespersonIdOrderByCreatedAtAsc(UUID companyId, UUID salespersonId);

    List<CommissionRule> findAllByCompanyIdAndSalespersonIdAndActiveTrue(UUID companyId, UUID salespersonId);
}
