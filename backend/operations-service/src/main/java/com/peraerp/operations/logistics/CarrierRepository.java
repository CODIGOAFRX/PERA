package com.peraerp.operations.logistics;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CarrierRepository extends JpaRepository<Carrier, UUID> {
    Optional<Carrier> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select c from Carrier c where c.companyId = :companyId " +
            "and (:filterActive = false or c.active = :active) " +
            "and (:filterOwnership = false or c.ownership = :ownership) " +
            "and (:filterQuery = false or lower(c.code) like lower(concat('%', :query, '%')) " +
            "or lower(c.name) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(c.taxIdentifier, '')) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(c.externalIdentifier, '')) like lower(concat('%', :query, '%')))")
    Page<Carrier> search(@Param("companyId") UUID companyId,
                         @Param("filterActive") boolean filterActive,
                         @Param("active") Boolean active,
                         @Param("filterOwnership") boolean filterOwnership,
                         @Param("ownership") CarrierOwnership ownership,
                         @Param("filterQuery") boolean filterQuery,
                         @Param("query") String query,
                         Pageable pageable);
}
