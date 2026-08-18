package com.peraerp.activity.alert;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AlertInstanceRepository extends JpaRepository<AlertInstance, UUID> {
    Optional<AlertInstance> findByIdAndCompanyId(UUID id, UUID companyId);
    Page<AlertInstance> findAllByCompanyId(UUID companyId, Pageable pageable);
    Page<AlertInstance> findAllByCompanyIdAndStatus(UUID companyId, AlertStatus status, Pageable pageable);
    boolean existsByCompanyIdAndRuleIdAndDedupeKeyAndCreatedAtGreaterThanEqual(
            UUID companyId, UUID ruleId, String dedupeKey, Instant threshold);
}
