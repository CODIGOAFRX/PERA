package com.peraerp.operations.claims;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    Optional<Claim> findByIdAndCompanyId(UUID id, UUID companyId);

    /** {@code overdueOn} limita a las abiertas cuyo plazo de seguimiento ya pasó en esa fecha. */
    @Query("select c from Claim c where c.companyId = :companyId " +
            "and (:filterStatus = false or c.status = :status) " +
            "and (:filterCustomer = false or c.customerId = :customerId) " +
            "and (:filterReason = false or c.reasonId = :reasonId) " +
            "and (:filterFrom = false or c.claimDate >= :fromDate) " +
            "and (:filterTo = false or c.claimDate <= :toDate) " +
            "and (:filterOverdue = false or (c.status = com.peraerp.operations.claims.ClaimStatus.OPEN " +
            "and c.followUpDate < :overdueOn)) " +
            "and (:filterQuery = false or lower(c.number) like lower(concat('%', :query, '%')) " +
            "or lower(c.customerNameSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(c.sourceDocumentNumber) like lower(concat('%', :query, '%')) " +
            "or lower(c.description) like lower(concat('%', :query, '%')))")
    Page<Claim> search(@Param("companyId") UUID companyId,
                       @Param("filterStatus") boolean filterStatus, @Param("status") ClaimStatus status,
                       @Param("filterCustomer") boolean filterCustomer, @Param("customerId") UUID customerId,
                       @Param("filterReason") boolean filterReason, @Param("reasonId") UUID reasonId,
                       @Param("filterFrom") boolean filterFrom, @Param("fromDate") LocalDate fromDate,
                       @Param("filterTo") boolean filterTo, @Param("toDate") LocalDate toDate,
                       @Param("filterOverdue") boolean filterOverdue, @Param("overdueOn") LocalDate overdueOn,
                       @Param("filterQuery") boolean filterQuery, @Param("query") String query,
                       Pageable pageable);
}
