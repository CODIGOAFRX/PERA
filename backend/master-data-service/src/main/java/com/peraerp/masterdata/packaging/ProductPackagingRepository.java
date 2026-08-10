package com.peraerp.masterdata.packaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductPackagingRepository extends JpaRepository<ProductPackaging, UUID> {
    Optional<ProductPackaging> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    boolean existsByCompanyIdAndProductIdAndDefaultPackagingTrueAndActiveTrue(UUID companyId, UUID productId);
    boolean existsByCompanyIdAndProductIdAndDefaultPackagingTrueAndActiveTrueAndIdNot(
            UUID companyId, UUID productId, UUID id);
    boolean existsByCompanyIdAndPackagingTypeIdAndActiveTrue(UUID companyId, UUID packagingTypeId);
    boolean existsByCompanyIdAndProductIdAndActiveTrue(UUID companyId, UUID productId);

    @Query("select p from ProductPackaging p where p.companyId = :companyId " +
            "and (:query = '' or lower(coalesce(p.code, '')) like lower(concat('%', :query, '%'))) " +
            "and (:productId is null or p.productId = :productId) " +
            "and (:packagingTypeId is null or p.packagingTypeId = :packagingTypeId) " +
            "and (:defaultPackaging is null or p.defaultPackaging = :defaultPackaging) " +
            "and (:active is null or p.active = :active)")
    Page<ProductPackaging> search(@Param("companyId") UUID companyId, @Param("query") String query,
                                  @Param("productId") UUID productId,
                                  @Param("packagingTypeId") UUID packagingTypeId,
                                  @Param("defaultPackaging") Boolean defaultPackaging,
                                  @Param("active") Boolean active, Pageable pageable);
}
