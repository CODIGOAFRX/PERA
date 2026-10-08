package com.peraerp.masterdata.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalespersonRepository extends JpaRepository<Salesperson, UUID> {
    Optional<Salesperson> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select s from Salesperson s where s.companyId = :companyId " +
            "and (:active is null or s.active = :active) order by lower(s.name) asc, s.code asc")
    List<Salesperson> search(@Param("companyId") UUID companyId, @Param("active") Boolean active);
}
