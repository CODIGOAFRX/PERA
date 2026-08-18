package com.peraerp.identity.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanySettingsRepository extends JpaRepository<CompanySettings, UUID> {
    Optional<CompanySettings> findByCompanyId(UUID companyId);
}
