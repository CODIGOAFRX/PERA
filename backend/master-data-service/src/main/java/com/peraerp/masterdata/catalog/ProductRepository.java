package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    boolean existsByCompanyIdAndProductGroupId(UUID companyId, UUID productGroupId);

    @Query("select p from Product p where p.companyId = :companyId and (:query is null " +
            "or lower(p.code) like lower(concat('%', :query, '%')) " +
            "or lower(p.name) like lower(concat('%', :query, '%')))")
    Page<Product> search(@Param("companyId") UUID companyId, @Param("query") String query, Pageable pageable);
}
