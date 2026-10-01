package com.peraerp.operations.salesdelivery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesDeliveryRepository extends JpaRepository<SalesDelivery, UUID> {

    Optional<SalesDelivery> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<SalesDelivery> findByCompanyIdAndSourceDocumentId(UUID companyId, UUID sourceDocumentId);

    List<SalesDelivery> findAllByCompanyIdAndStatusOrderBySourceDateAscCreatedAtAsc(UUID companyId,
                                                                                   SalesDeliveryStatus status);

    @Query("select d from SalesDelivery d where d.companyId = :companyId " +
            "and (:filterStatus = false or d.status = :status) " +
            "and (:filterQuery = false or lower(d.sourceNumber) like lower(concat('%', :query, '%')) " +
            "or lower(d.customerName) like lower(concat('%', :query, '%')))")
    Page<SalesDelivery> search(@Param("companyId") UUID companyId,
                               @Param("filterStatus") boolean filterStatus,
                               @Param("status") SalesDeliveryStatus status,
                               @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                               Pageable pageable);
}
