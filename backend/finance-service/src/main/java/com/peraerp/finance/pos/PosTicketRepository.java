package com.peraerp.finance.pos;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosTicketRepository extends JpaRepository<PosTicket, UUID> {

    Optional<PosTicket> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<PosTicket> findByCompanyIdAndTicketNumberIgnoreCase(UUID companyId, String ticketNumber);

    Optional<PosTicket> findByCompanyIdAndClientReference(UUID companyId, UUID clientReference);

    /** Bloquea la venta mientras se devuelve o anula, para no devolver dos veces las mismas unidades. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PosTicket t where t.id = :id and t.companyId = :companyId")
    Optional<PosTicket> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    List<PosTicket> findAllByCompanyIdAndCashSessionIdOrderByIssuedAtAsc(UUID companyId, UUID cashSessionId);

    List<PosTicket> findAllByCompanyIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThanOrderByIssuedAtAsc(
            UUID companyId, Instant from, Instant to);

    boolean existsByCompanyIdAndOriginalTicketId(UUID companyId, UUID originalTicketId);

    @Query("select t from PosTicket t where t.companyId = :companyId "
            + "and (:filterSession = false or t.cashSessionId = :sessionId) "
            + "and (:filterRegister = false or t.cashRegisterId = :registerId) "
            + "and (:filterKind = false or t.kind = :kind) "
            + "and (:filterFrom = false or t.issuedAt >= :issuedFrom) "
            + "and (:filterTo = false or t.issuedAt < :issuedTo) "
            + "and (:filterQuery = false or lower(t.ticketNumber) like lower(concat('%', :query, '%')) "
            + "or lower(t.cashierName) like lower(concat('%', :query, '%')) "
            + "or lower(coalesce(t.customerName, '')) like lower(concat('%', :query, '%')))")
    Page<PosTicket> search(@Param("companyId") UUID companyId,
                           @Param("filterSession") boolean filterSession, @Param("sessionId") UUID sessionId,
                           @Param("filterRegister") boolean filterRegister, @Param("registerId") UUID registerId,
                           @Param("filterKind") boolean filterKind, @Param("kind") PosTicketKind kind,
                           @Param("filterFrom") boolean filterFrom, @Param("issuedFrom") Instant issuedFrom,
                           @Param("filterTo") boolean filterTo, @Param("issuedTo") Instant issuedTo,
                           @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                           Pageable pageable);

    /** Tickets cambiados desde un momento, para que operations-service descuente o devuelva existencias. */
    @Query("select t from PosTicket t where t.companyId = :companyId and t.updatedAt > :updatedSince "
            + "order by t.updatedAt asc")
    List<PosTicket> findUpdatedSince(@Param("companyId") UUID companyId, @Param("updatedSince") Instant updatedSince,
                                     Pageable pageable);
}
