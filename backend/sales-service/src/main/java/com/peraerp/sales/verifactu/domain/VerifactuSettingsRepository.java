package com.peraerp.sales.verifactu.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerifactuSettingsRepository extends JpaRepository<VerifactuSettings, UUID> {

    Optional<VerifactuSettings> findByCompanyId(UUID companyId);
}
