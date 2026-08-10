package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductSupertypeRepository extends JpaRepository<ProductSupertype, UUID> {
    Optional<ProductSupertype> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select s from ProductSupertype s where s.companyId = :companyId " +
            "and (:natureId is null or s.natureId = :natureId) " +
            "and (:active is null or s.active = :active) " +
            "and (:query = '' or lower(s.code) like lower(concat('%', :query, '%')) " +
            "or lower(s.name) like lower(concat('%', :query, '%')))")
    Page<ProductSupertype> search(@Param("companyId") UUID companyId, @Param("query") String query,
                                  @Param("natureId") UUID natureId, @Param("active") Boolean active,
                                  Pageable pageable);
}
