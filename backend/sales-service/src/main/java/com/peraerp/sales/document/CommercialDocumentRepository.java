package com.peraerp.sales.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommercialDocumentRepository extends JpaRepository<CommercialDocument, UUID> {
    Optional<CommercialDocument> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Issued invoices of a customer that are not fully collected, in the company base currency. */
    @Query("select coalesce(sum(d.baseTotalAmount), 0) from CommercialDocument d where d.companyId = :companyId " +
            "and d.customerId = :customerId " +
            "and d.type in (com.peraerp.sales.document.DocumentType.INVOICE, com.peraerp.sales.document.DocumentType.RECTIFYING_INVOICE) " +
            "and d.status = com.peraerp.sales.document.DocumentStatus.CONFIRMED " +
            "and d.paymentStatus in (com.peraerp.sales.document.PaymentStatus.PENDING, com.peraerp.sales.document.PaymentStatus.PARTIALLY_PAID)")
    java.math.BigDecimal sumOutstandingInvoices(@Param("companyId") UUID companyId, @Param("customerId") UUID customerId);
    Optional<CommercialDocument> findByIdAndCompanyIdAndType(UUID id, UUID companyId, DocumentType type);
    boolean existsByCompanyIdAndTypeAndDocumentNumber(UUID companyId, DocumentType type, String documentNumber);
    List<CommercialDocument> findAllByCompanyIdAndTypeInAndStatusInOrderByIssueDateDesc(
            UUID companyId, List<DocumentType> types, List<DocumentStatus> statuses);

    /** Albaranes y facturas sin albarán previo que entregan mercancía, del más antiguo al más reciente. */
    @Query("select d from CommercialDocument d where d.companyId = :companyId " +
            "and (d.type = com.peraerp.sales.document.DocumentType.DELIVERY_NOTE " +
            "or (d.type = com.peraerp.sales.document.DocumentType.INVOICE and d.sourceDocumentId is null)) " +
            "and d.status <> com.peraerp.sales.document.DocumentStatus.DRAFT " +
            "and d.updatedAt >= :updatedSince order by d.updatedAt asc, d.id asc")
    List<CommercialDocument> findDeliveriesUpdatedSince(@Param("companyId") UUID companyId,
                                                        @Param("updatedSince") java.time.Instant updatedSince,
                                                        Pageable pageable);

    /**
     * Facturas y rectificativas expedidas con comercial, para calcular comisiones. Dos consultas, con y sin
     * comercial, porque un parámetro nulo comparado con «is null» no tiene tipo para PostgreSQL.
     */
    String COMMISSIONABLE = "select d from CommercialDocument d where d.companyId = :companyId " +
            "and d.salespersonId is not null " +
            "and d.type in (com.peraerp.sales.document.DocumentType.INVOICE, " +
            "com.peraerp.sales.document.DocumentType.RECTIFYING_INVOICE) " +
            "and d.status in (com.peraerp.sales.document.DocumentStatus.CONFIRMED, " +
            "com.peraerp.sales.document.DocumentStatus.CONVERTED) " +
            "and d.issueDate >= :fromDate and d.issueDate <= :toDate ";

    @Query(COMMISSIONABLE + "order by d.issueDate, d.documentNumber")
    List<CommercialDocument> findForCommissions(@Param("companyId") UUID companyId,
                                                @Param("fromDate") LocalDate fromDate,
                                                @Param("toDate") LocalDate toDate);

    @Query(COMMISSIONABLE + "and d.salespersonId = :salespersonId order by d.issueDate, d.documentNumber")
    List<CommercialDocument> findForCommissionsOf(@Param("companyId") UUID companyId,
                                                  @Param("salespersonId") UUID salespersonId,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("toDate") LocalDate toDate);

    /*
     * Los filtros opcionales van con una marca booleana: con «:x is null» PostgreSQL no puede
     * deducir el tipo de un parámetro nulo y la consulta fallaba (error 500) al combinar fechas
     * sin cliente, por ejemplo en los informes de cobros por periodo.
     */
    default Page<CommercialDocument> search(UUID companyId, String query, DocumentType type, DocumentStatus status,
                                            UUID customerId, LocalDate fromDate, LocalDate toDate,
                                            Pageable pageable) {
        return searchFiltered(companyId, query, type != null, type, status != null, status, customerId != null,
                customerId, fromDate != null, fromDate, toDate != null, toDate, pageable);
    }

    @Query("select d from CommercialDocument d where d.companyId = :companyId " +
            "and (:query = '' or lower(d.documentNumber) like concat('%', lower(:query), '%') " +
            "or lower(d.customerCodeSnapshot) like concat('%', lower(:query), '%') " +
            "or lower(d.customerNameSnapshot) like concat('%', lower(:query), '%')) " +
            "and (:filterType = false or d.type = :type) and (:filterStatus = false or d.status = :status) " +
            "and (:filterCustomer = false or d.customerId = :customerId) " +
            "and (:filterFrom = false or d.issueDate >= :fromDate) and (:filterTo = false or d.issueDate <= :toDate)")
    Page<CommercialDocument> searchFiltered(@Param("companyId") UUID companyId, @Param("query") String query,
                                            @Param("filterType") boolean filterType, @Param("type") DocumentType type,
                                            @Param("filterStatus") boolean filterStatus,
                                            @Param("status") DocumentStatus status,
                                            @Param("filterCustomer") boolean filterCustomer,
                                            @Param("customerId") UUID customerId,
                                            @Param("filterFrom") boolean filterFrom, @Param("fromDate") LocalDate fromDate,
                                            @Param("filterTo") boolean filterTo, @Param("toDate") LocalDate toDate,
                                            Pageable pageable);

    default Page<CommercialDocument> searchQuotes(UUID companyId, QuoteStatus status, UUID customerId,
                                                  LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        return searchQuotesFiltered(companyId, status != null, status, customerId != null, customerId,
                fromDate != null, fromDate, toDate != null, toDate, pageable);
    }

    @Query("select d from CommercialDocument d where d.companyId = :companyId and d.type = com.peraerp.sales.document.DocumentType.QUOTE " +
            "and (:filterStatus = false or d.quoteStatus = :status) " +
            "and (:filterCustomer = false or d.customerId = :customerId) " +
            "and (:filterFrom = false or d.issueDate >= :fromDate) and (:filterTo = false or d.issueDate <= :toDate)")
    Page<CommercialDocument> searchQuotesFiltered(@Param("companyId") UUID companyId,
                                                  @Param("filterStatus") boolean filterStatus,
                                                  @Param("status") QuoteStatus status,
                                                  @Param("filterCustomer") boolean filterCustomer,
                                                  @Param("customerId") UUID customerId,
                                                  @Param("filterFrom") boolean filterFrom,
                                                  @Param("fromDate") LocalDate fromDate,
                                                  @Param("filterTo") boolean filterTo, @Param("toDate") LocalDate toDate,
                                                  Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CommercialDocument d set d.quoteStatus = com.peraerp.sales.document.QuoteStatus.EXPIRED, " +
            "d.status = com.peraerp.sales.document.DocumentStatus.CANCELLED, d.quoteDecidedAt = CURRENT_TIMESTAMP " +
            "where d.companyId = :companyId and d.type = com.peraerp.sales.document.DocumentType.QUOTE " +
            "and d.quoteStatus = com.peraerp.sales.document.QuoteStatus.SENT and d.quoteValidUntil < :today")
    int expireDueQuotes(@Param("companyId") UUID companyId, @Param("today") LocalDate today);

    @Query("select new com.peraerp.sales.dashboard.InvoiceRevenueEntry(d.issueDate, d.baseTotalAmount) " +
            "from CommercialDocument d where d.companyId = :companyId " +
            "and d.type = com.peraerp.sales.document.DocumentType.INVOICE " +
            "and d.status in (com.peraerp.sales.document.DocumentStatus.CONFIRMED, " +
            "com.peraerp.sales.document.DocumentStatus.CONVERTED) " +
            "and d.issueDate between :fromDate and :toDate order by d.issueDate")
    List<com.peraerp.sales.dashboard.InvoiceRevenueEntry> findInvoiceRevenue(
            @Param("companyId") UUID companyId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
