package com.peraerp.operations.freight;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FreightRateRepository extends JpaRepository<FreightRate, UUID> {

    Optional<FreightRate> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select r from FreightRate r where r.companyId = :companyId " +
            "and (:filterActive = false or r.active = :active) " +
            "and (:filterMethod = false or r.calculationMethod = :method) " +
            "and (:filterRoute = false or r.routeId = :routeId) " +
            "and (:filterCarrier = false or r.carrierId = :carrierId) " +
            "and (:filterValidOn = false or (r.validFrom <= :validOn and (r.validTo is null or r.validTo >= :validOn))) " +
            "and (:filterQuery = false or lower(r.code) like lower(concat('%', :query, '%')) " +
            "or lower(r.name) like lower(concat('%', :query, '%'))) ")
    Page<FreightRate> search(@Param("companyId") UUID companyId,
                             @Param("filterActive") boolean filterActive, @Param("active") boolean active,
                             @Param("filterMethod") boolean filterMethod,
                             @Param("method") FreightCalculationMethod method,
                             @Param("filterRoute") boolean filterRoute, @Param("routeId") UUID routeId,
                             @Param("filterCarrier") boolean filterCarrier, @Param("carrierId") UUID carrierId,
                             @Param("filterValidOn") boolean filterValidOn, @Param("validOn") LocalDate validOn,
                             @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                             Pageable pageable);

    @Query("select r from FreightRate r where r.companyId = :companyId and r.active = true " +
            "and r.currencyCode = :currencyCode and r.validFrom <= :pricingDate " +
            "and (r.validTo is null or r.validTo >= :pricingDate)")
    List<FreightRate> findCandidates(@Param("companyId") UUID companyId,
                                     @Param("currencyCode") String currencyCode,
                                     @Param("pricingDate") LocalDate pricingDate);
}
