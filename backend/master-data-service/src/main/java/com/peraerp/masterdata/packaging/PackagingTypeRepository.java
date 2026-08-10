package com.peraerp.masterdata.packaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PackagingTypeRepository extends JpaRepository<PackagingType, UUID> {
    Optional<PackagingType> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select p from PackagingType p where p.companyId = :companyId " +
            "and (:query = '' or lower(p.code) like lower(concat('%', :query, '%')) " +
            "or lower(p.name) like lower(concat('%', :query, '%'))) " +
            "and (:returnable is null or p.returnable = :returnable) " +
            "and (:active is null or p.active = :active)")
    Page<PackagingType> search(@Param("companyId") UUID companyId, @Param("query") String query,
                               @Param("returnable") Boolean returnable, @Param("active") Boolean active,
                               Pageable pageable);
}
