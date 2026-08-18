package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TaxCodeRepository extends JpaRepository<TaxCode, UUID> {
    Optional<TaxCode> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCountryCodeAndCodeIgnoreCase(UUID companyId, String countryCode, String code);

    @Query("select t from TaxCode t where t.companyId = :companyId " +
            "and (:countryCode is null or t.countryCode = :countryCode) " +
            "and (:active is null or t.active = :active) " +
            "and (:validOn is null or (t.validFrom <= :validOn and (t.validUntil is null or t.validUntil >= :validOn))) " +
            "and (:query = '' or lower(t.code) like lower(concat('%', :query, '%')) " +
            "or lower(t.name) like lower(concat('%', :query, '%')))")
    Page<TaxCode> search(@Param("companyId") UUID companyId, @Param("query") String query,
                         @Param("countryCode") String countryCode, @Param("active") Boolean active,
                         @Param("validOn") LocalDate validOn, Pageable pageable);
}
