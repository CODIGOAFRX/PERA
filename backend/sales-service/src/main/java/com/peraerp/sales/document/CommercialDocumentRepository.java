package com.peraerp.sales.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CommercialDocumentRepository extends JpaRepository<CommercialDocument, UUID> {
    Optional<CommercialDocument> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<CommercialDocument> findByIdAndCompanyIdAndType(UUID id, UUID companyId, DocumentType type);

    @Query("select d from CommercialDocument d where d.companyId = :companyId " +
            "and (:type is null or d.type = :type) and (:status is null or d.status = :status) " +
            "and (:customerId is null or d.customerId = :customerId) " +
            "and (:fromDate is null or d.issueDate >= :fromDate) and (:toDate is null or d.issueDate <= :toDate)")
    Page<CommercialDocument> search(@Param("companyId") UUID companyId, @Param("type") DocumentType type,
                                    @Param("status") DocumentStatus status, @Param("customerId") UUID customerId,
                                    @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                    Pageable pageable);

    @Query("select d from CommercialDocument d where d.companyId = :companyId and d.type = com.peraerp.sales.document.DocumentType.QUOTE " +
            "and (:status is null or d.quoteStatus = :status) and (:customerId is null or d.customerId = :customerId) " +
            "and (:fromDate is null or d.issueDate >= :fromDate) and (:toDate is null or d.issueDate <= :toDate)")
    Page<CommercialDocument> searchQuotes(@Param("companyId") UUID companyId, @Param("status") QuoteStatus status,
                                          @Param("customerId") UUID customerId,
                                          @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                          Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommercialDocument d set d.quoteStatus = com.peraerp.sales.document.QuoteStatus.EXPIRED, " +
            "d.status = com.peraerp.sales.document.DocumentStatus.CANCELLED, d.quoteDecidedAt = CURRENT_TIMESTAMP " +
            "where d.companyId = :companyId and d.type = com.peraerp.sales.document.DocumentType.QUOTE " +
            "and d.quoteStatus = com.peraerp.sales.document.QuoteStatus.SENT and d.quoteValidUntil < :today")
    int expireDueQuotes(@Param("companyId") UUID companyId, @Param("today") LocalDate today);
}
