package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductGroupRepository extends JpaRepository<ProductGroup, UUID> {
    Optional<ProductGroup> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select g from ProductGroup g where g.companyId = :companyId " +
            "and (:productTypeId is null or g.productTypeId = :productTypeId) " +
            "and (:active is null or g.active = :active) " +
            "and (:query = '' or lower(g.code) like lower(concat('%', :query, '%')) " +
            "or lower(g.name) like lower(concat('%', :query, '%')))")
    Page<ProductGroup> search(@Param("companyId") UUID companyId, @Param("query") String query,
                              @Param("productTypeId") UUID productTypeId, @Param("active") Boolean active,
                              Pageable pageable);
}
