package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceListRepository extends JpaRepository<PriceList, UUID> {
    Optional<PriceList> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    List<PriceList> findAllByCompanyIdAndScope(UUID companyId, PricingScope scope);
    List<PriceList> findAllByCompanyIdAndParentPriceListId(UUID companyId, UUID parentPriceListId);

    @Query("select p from PriceList p where p.companyId = :companyId " +
            "and (:query = '' or lower(p.code) like lower(concat('%', :query, '%')) " +
            "or lower(p.name) like lower(concat('%', :query, '%'))) " +
            "and (:customerId is null or p.customerId = :customerId) " +
            "and (:natureId is null or p.productNatureId = :natureId) " +
            "and (:supertypeId is null or p.productSupertypeId = :supertypeId) " +
            "and (:typeId is null or p.productTypeId = :typeId) " +
            "and (:scope is null or p.scope = :scope) " +
            "and (:active is null or p.active = :active) " +
            "and (:validOn is null or (p.validFrom <= :validOn and (p.validUntil is null or p.validUntil >= :validOn)))")
    Page<PriceList> search(@Param("companyId") UUID companyId, @Param("query") String query,
                           @Param("customerId") UUID customerId, @Param("natureId") UUID natureId,
                           @Param("supertypeId") UUID supertypeId, @Param("typeId") UUID typeId,
                           @Param("scope") PricingScope scope, @Param("active") Boolean active,
                           @Param("validOn") LocalDate validOn, Pageable pageable);

    @Query("select p from PriceList p where p.companyId = :companyId and p.currency = :currency " +
            "and p.active = true and p.validFrom <= :date and (p.validUntil is null or p.validUntil >= :date)")
    List<PriceList> findResolutionCandidates(@Param("companyId") UUID companyId,
                                             @Param("currency") String currency,
                                             @Param("date") LocalDate date);
}
