package com.peraerp.operations.purchasing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseDocumentRepository extends JpaRepository<PurchaseDocument, UUID> {

    Optional<PurchaseDocument> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndSourceDocumentIdAndStatusNotAndIdNot(UUID companyId, UUID sourceDocumentId,
                                                                     PurchaseDocumentStatus status, UUID id);

    @Query("select count(d) > 0 from PurchaseDocument d where d.companyId = :companyId " +
            "and d.type = com.peraerp.operations.purchasing.PurchaseDocumentType.SUPPLIER_INVOICE " +
            "and d.supplierId = :supplierId and lower(d.supplierReference) = lower(:supplierReference) " +
            "and d.status in :statuses and d.id <> :excludedId")
    boolean existsSupplierInvoice(@Param("companyId") UUID companyId, @Param("supplierId") UUID supplierId,
                                  @Param("supplierReference") String supplierReference,
                                  @Param("statuses") Collection<PurchaseDocumentStatus> statuses,
                                  @Param("excludedId") UUID excludedId);

    @Query("select d from PurchaseDocument d where d.companyId = :companyId " +
            "and (:filterType = false or d.type = :type) " +
            "and (:filterStatus = false or d.status = :status) " +
            "and (:filterSupplier = false or d.supplierId = :supplierId) " +
            "and (:filterFrom = false or d.issueDate >= :fromDate) " +
            "and (:filterTo = false or d.issueDate <= :toDate) " +
            "and (:filterQuery = false or lower(d.number) like lower(concat('%', :query, '%')) " +
            "or lower(d.supplierNameSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(d.supplierCodeSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(d.supplierReference) like lower(concat('%', :query, '%')))")
    Page<PurchaseDocument> search(@Param("companyId") UUID companyId,
                                  @Param("filterType") boolean filterType, @Param("type") PurchaseDocumentType type,
                                  @Param("filterStatus") boolean filterStatus,
                                  @Param("status") PurchaseDocumentStatus status,
                                  @Param("filterSupplier") boolean filterSupplier,
                                  @Param("supplierId") UUID supplierId,
                                  @Param("filterFrom") boolean filterFrom, @Param("fromDate") LocalDate fromDate,
                                  @Param("filterTo") boolean filterTo, @Param("toDate") LocalDate toDate,
                                  @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                                  Pageable pageable);
}
