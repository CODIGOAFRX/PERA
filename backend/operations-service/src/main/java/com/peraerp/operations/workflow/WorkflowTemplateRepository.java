package com.peraerp.operations.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkflowTemplateRepository extends JpaRepository<WorkflowTemplate, UUID> {
    Optional<WorkflowTemplate> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Query("select max(t.templateVersion) from WorkflowTemplate t " +
            "where t.companyId = :companyId and lower(t.code) = lower(:code)")
    Integer findMaxVersion(@Param("companyId") UUID companyId, @Param("code") String code);

    @Query("select t from WorkflowTemplate t where t.companyId = :companyId " +
            "and (:filterStatus = false or t.status = :status) " +
            "and (:filterReferenceType = false or t.referenceType = :referenceType) " +
            "and (:filterQuery = false or lower(t.code) like lower(concat('%', :query, '%')) " +
            "or lower(t.name) like lower(concat('%', :query, '%')))")
    Page<WorkflowTemplate> search(@Param("companyId") UUID companyId,
                                  @Param("filterStatus") boolean filterStatus,
                                  @Param("status") WorkflowTemplateStatus status,
                                  @Param("filterReferenceType") boolean filterReferenceType,
                                  @Param("referenceType") String referenceType,
                                  @Param("filterQuery") boolean filterQuery,
                                  @Param("query") String query,
                                  Pageable pageable);
}
