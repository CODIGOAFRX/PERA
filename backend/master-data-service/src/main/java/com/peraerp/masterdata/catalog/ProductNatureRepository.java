package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductNatureRepository extends JpaRepository<ProductNature, UUID> {
    Optional<ProductNature> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select n from ProductNature n where n.companyId = :companyId " +
            "and (:active is null or n.active = :active) " +
            "and (:query = '' or lower(n.code) like lower(concat('%', :query, '%')) " +
            "or lower(n.name) like lower(concat('%', :query, '%')))")
    Page<ProductNature> search(@Param("companyId") UUID companyId, @Param("query") String query,
                               @Param("active") Boolean active, Pageable pageable);
}
