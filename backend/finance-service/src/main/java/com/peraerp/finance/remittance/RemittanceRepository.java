package com.peraerp.finance.remittance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RemittanceRepository extends JpaRepository<Remittance, UUID> {

    Optional<Remittance> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select r from Remittance r where r.companyId = :companyId " +
            "and (:filterStatus = false or r.status = :status)")
    Page<Remittance> search(@Param("companyId") UUID companyId, @Param("filterStatus") boolean filterStatus,
                            @Param("status") RemittanceStatus status, Pageable pageable);
}
