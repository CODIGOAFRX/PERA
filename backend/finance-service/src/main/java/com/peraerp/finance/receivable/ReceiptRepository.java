package com.peraerp.finance.receivable;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Receipt> findAllByCompanyIdAndIdIn(UUID companyId, Collection<UUID> ids);

    List<Receipt> findAllByCompanyIdAndDocumentIdOrderByInstallmentNumberAsc(UUID companyId, UUID documentId);

    List<Receipt> findAllByCompanyIdAndRemittanceIdOrderByDueDateAscReceiptNumberAsc(UUID companyId, UUID remittanceId);

    boolean existsByCompanyIdAndDueDateIdAndStatusNot(UUID companyId, UUID dueDateId, ReceiptStatus status);

    /** {@code available} limita a los recibos pendientes que todavía no están en ninguna remesa. */
    @Query("select r from Receipt r where r.companyId = :companyId " +
            "and (:filterStatus = false or r.status = :status) " +
            "and (:filterCustomer = false or r.customerId = :customerId) " +
            "and (:filterFrom = false or r.dueDate >= :dueFrom) " +
            "and (:filterTo = false or r.dueDate <= :dueTo) " +
            "and (:available = false or (r.status = com.peraerp.finance.receivable.ReceiptStatus.PENDING " +
            "and r.remittanceId is null)) " +
            "and (:filterQuery = false or lower(r.receiptNumber) like lower(concat('%', :query, '%')) " +
            "or lower(r.documentNumberSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(r.customerNameSnapshot) like lower(concat('%', :query, '%')))")
    Page<Receipt> search(@Param("companyId") UUID companyId,
                         @Param("filterStatus") boolean filterStatus, @Param("status") ReceiptStatus status,
                         @Param("filterCustomer") boolean filterCustomer, @Param("customerId") UUID customerId,
                         @Param("filterFrom") boolean filterFrom, @Param("dueFrom") LocalDate dueFrom,
                         @Param("filterTo") boolean filterTo, @Param("dueTo") LocalDate dueTo,
                         @Param("available") boolean available,
                         @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                         Pageable pageable);
}
