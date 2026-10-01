package com.peraerp.operations.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    boolean existsByCompanyIdAndWarehouseId(UUID companyId, UUID warehouseId);

    List<StockMovement> findAllByCompanyIdAndSourceTypeAndSourceIdAndType(UUID companyId, StockSourceType sourceType,
                                                                          UUID sourceId, StockMovementType type);

    @Query("select m from StockMovement m where m.companyId = :companyId " +
            "and (:filterWarehouse = false or m.warehouseId = :warehouseId) " +
            "and (:filterProduct = false or m.productId = :productId) " +
            "and (:filterType = false or m.type = :type) " +
            "and (:filterFrom = false or m.occurredAt >= :occurredFrom) " +
            "and (:filterTo = false or m.occurredAt < :occurredTo) " +
            "and (:filterQuery = false or lower(m.productCodeSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(m.productNameSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(m.sourceNumberSnapshot) like lower(concat('%', :query, '%')))")
    Page<StockMovement> search(@Param("companyId") UUID companyId,
                               @Param("filterWarehouse") boolean filterWarehouse,
                               @Param("warehouseId") UUID warehouseId,
                               @Param("filterProduct") boolean filterProduct, @Param("productId") UUID productId,
                               @Param("filterType") boolean filterType, @Param("type") StockMovementType type,
                               @Param("filterFrom") boolean filterFrom, @Param("occurredFrom") Instant occurredFrom,
                               @Param("filterTo") boolean filterTo, @Param("occurredTo") Instant occurredTo,
                               @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                               Pageable pageable);
}
