package com.peraerp.masterdata.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductTypeRepository extends JpaRepository<ProductType, UUID> {
    Optional<ProductType> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select t from ProductType t where t.companyId = :companyId " +
            "and (:supertypeId is null or t.supertypeId = :supertypeId) " +
            "and (:active is null or t.active = :active) " +
            "and (:query = '' or lower(t.code) like lower(concat('%', :query, '%')) " +
            "or lower(t.name) like lower(concat('%', :query, '%')))")
    Page<ProductType> search(@Param("companyId") UUID companyId, @Param("query") String query,
                             @Param("supertypeId") UUID supertypeId, @Param("active") Boolean active,
                             Pageable pageable);
}
