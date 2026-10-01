package com.peraerp.operations.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    Optional<Warehouse> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Warehouse> findByCompanyIdAndDefaultWarehouseTrue(UUID companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select w from Warehouse w where w.companyId = :companyId " +
            "and (:filterActive = false or w.active = :active) " +
            "and (:filterQuery = false or lower(w.code) like lower(concat('%', :query, '%')) " +
            "or lower(w.name) like lower(concat('%', :query, '%')))")
    Page<Warehouse> search(@Param("companyId") UUID companyId,
                           @Param("filterActive") boolean filterActive, @Param("active") boolean active,
                           @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                           Pageable pageable);
}
