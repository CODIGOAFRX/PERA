package com.peraerp.sales.commission;

import com.peraerp.sales.document.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesCommissionRepository extends JpaRepository<SalesCommission, UUID> {
    Optional<SalesCommission> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<SalesCommission> findByCompanyIdAndDocumentId(UUID companyId, UUID documentId);

    List<SalesCommission> findAllByCompanyIdAndIdIn(UUID companyId, List<UUID> ids);

    String FILTER = "from SalesCommission c, CommercialDocument d where d.id = c.documentId and c.companyId = :companyId " +
            "and (:salespersonId is null or c.salespersonId = :salespersonId) " +
            "and (:status is null or c.status = :status) " +
            "and (:fromDate is null or d.issueDate >= :fromDate) " +
            "and (:toDate is null or d.issueDate <= :toDate) " +
            "and (:collected is null or (:collected = true and d.paymentStatus = :paid) " +
            "or (:collected = false and d.paymentStatus <> :paid))";

    @Query(value = "select c " + FILTER + " order by d.issueDate desc, d.documentNumber desc",
            countQuery = "select count(c) " + FILTER)
    Page<SalesCommission> search(@Param("companyId") UUID companyId, @Param("salespersonId") UUID salespersonId,
                                 @Param("status") SalesCommission.Status status, @Param("fromDate") LocalDate fromDate,
                                 @Param("toDate") LocalDate toDate, @Param("collected") Boolean collected,
                                 @Param("paid") PaymentStatus paid, Pageable pageable);

    @Query("select coalesce(sum(c.baseAmount), 0), coalesce(sum(c.commissionAmount), 0), count(c) " + FILTER)
    List<Object[]> totals(@Param("companyId") UUID companyId, @Param("salespersonId") UUID salespersonId,
                          @Param("status") SalesCommission.Status status, @Param("fromDate") LocalDate fromDate,
                          @Param("toDate") LocalDate toDate, @Param("collected") Boolean collected,
                          @Param("paid") PaymentStatus paid);
}
