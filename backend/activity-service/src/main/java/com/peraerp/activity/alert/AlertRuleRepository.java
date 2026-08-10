package com.peraerp.activity.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {
    List<AlertRule> findAllByCompanyIdOrderByName(UUID companyId);
    List<AlertRule> findAllByCompanyIdAndActiveTrue(UUID companyId);
    Optional<AlertRule> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
