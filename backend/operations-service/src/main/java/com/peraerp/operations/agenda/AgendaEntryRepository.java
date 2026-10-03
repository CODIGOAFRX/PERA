package com.peraerp.operations.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaEntryRepository extends JpaRepository<AgendaEntry, UUID> {

    Optional<AgendaEntry> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Citas de un periodo, de la más temprana a la más tardía; las de todo el día, primero. */
    @Query("select e from AgendaEntry e where e.companyId = :companyId " +
            "and e.date >= :fromDate and e.date <= :toDate " +
            "and (:filterStatus = false or e.status = :status) " +
            "and (:filterType = false or e.typeId = :typeId) " +
            "and (:filterAssignee = false or lower(e.assigneeName) = lower(:assignee)) " +
            "and (:filterQuery = false or lower(e.title) like lower(concat('%', :query, '%')) " +
            "or lower(e.details) like lower(concat('%', :query, '%')) " +
            "or lower(e.customerNameSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(e.documentReference) like lower(concat('%', :query, '%')) " +
            "or lower(e.location) like lower(concat('%', :query, '%'))) " +
            "order by e.date asc, e.startTime asc nulls first, e.createdAt asc")
    List<AgendaEntry> findPeriod(@Param("companyId") UUID companyId,
                                 @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                                 @Param("filterStatus") boolean filterStatus, @Param("status") AgendaEntryStatus status,
                                 @Param("filterType") boolean filterType, @Param("typeId") UUID typeId,
                                 @Param("filterAssignee") boolean filterAssignee, @Param("assignee") String assignee,
                                 @Param("filterQuery") boolean filterQuery, @Param("query") String query);

    @Query("select distinct e.assigneeName from AgendaEntry e where e.companyId = :companyId " +
            "and e.assigneeName is not null order by e.assigneeName")
    List<String> findAssignees(@Param("companyId") UUID companyId);
}
