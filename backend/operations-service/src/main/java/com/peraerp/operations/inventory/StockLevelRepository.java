package com.peraerp.operations.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockLevelRepository extends JpaRepository<StockLevel, UUID> {

    /** Bloquea la fila para que dos movimientos simultáneos del mismo producto se apliquen en serie. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockLevel s where s.companyId = :companyId and s.warehouseId = :warehouseId " +
            "and s.productId = :productId")
    Optional<StockLevel> findForUpdate(@Param("companyId") UUID companyId, @Param("warehouseId") UUID warehouseId,
                                       @Param("productId") UUID productId);

    @Query("select s from StockLevel s where s.companyId = :companyId " +
            "and (:filterWarehouse = false or s.warehouseId = :warehouseId) " +
            "and (:filterProduct = false or s.productId = :productId) " +
            "and (:onlyInStock = false or s.quantity > 0) " +
            "and (:filterQuery = false or lower(s.productCodeSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(s.productNameSnapshot) like lower(concat('%', :query, '%')))")
    Page<StockLevel> search(@Param("companyId") UUID companyId,
                            @Param("filterWarehouse") boolean filterWarehouse, @Param("warehouseId") UUID warehouseId,
                            @Param("filterProduct") boolean filterProduct, @Param("productId") UUID productId,
                            @Param("onlyInStock") boolean onlyInStock,
                            @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                            Pageable pageable);
}
