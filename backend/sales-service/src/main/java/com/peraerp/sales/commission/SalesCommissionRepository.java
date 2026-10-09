package com.peraerp.sales.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesCommissionRepository extends JpaRepository<SalesCommission, UUID> {
    Optional<SalesCommission> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<SalesCommission> findByCompanyIdAndDocumentId(UUID companyId, UUID documentId);

    List<SalesCommission> findAllByCompanyIdAndIdIn(UUID companyId, List<UUID> ids);
}
