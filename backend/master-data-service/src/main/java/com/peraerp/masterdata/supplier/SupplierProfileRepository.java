package com.peraerp.masterdata.supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SupplierProfileRepository extends JpaRepository<SupplierProfile, UUID> {
    Optional<SupplierProfile> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select s from SupplierProfile s, Party p where s.partyId = p.id and s.companyId = :companyId " +
            "and (:query is null or lower(p.code) like lower(concat('%', :query, '%')) " +
            "or lower(p.legalName) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(p.tradeName, '')) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(p.taxId, '')) like lower(concat('%', :query, '%'))) " +
            "order by p.legalName asc, p.code asc")
    Page<SupplierProfile> search(@Param("companyId") UUID companyId, @Param("query") String query, Pageable pageable);
}
