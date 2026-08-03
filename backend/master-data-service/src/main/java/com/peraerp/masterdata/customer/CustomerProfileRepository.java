package com.peraerp.masterdata.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, UUID> {
    Optional<CustomerProfile> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query("select c from CustomerProfile c, Party p where c.partyId = p.id and c.companyId = :companyId " +
            "and (:query is null or lower(p.code) like lower(concat('%', :query, '%')) " +
            "or lower(p.legalName) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(p.tradeName, '')) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(p.taxId, '')) like lower(concat('%', :query, '%')))")
    Page<CustomerProfile> search(@Param("companyId") UUID companyId, @Param("query") String query, Pageable pageable);
}
