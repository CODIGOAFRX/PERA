package com.peraerp.operations.workflow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WorkExecutionRepository extends JpaRepository<WorkExecution, UUID> {
    Optional<WorkExecution> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndTemplateId(UUID companyId, UUID templateId);
    boolean existsByCompanyIdAndTemplateIdAndReferenceTypeAndReferenceId(
            UUID companyId, UUID templateId, String referenceType, UUID referenceId);

    @Query("select e from WorkExecution e where e.companyId = :companyId " +
            "and (:filterStatus = false or e.status = :status) " +
            "and (:filterReferenceType = false or e.referenceType = :referenceType) " +
            "and (:filterReferenceId = false or e.referenceId = :referenceId)")
    Page<WorkExecution> search(@Param("companyId") UUID companyId,
                               @Param("filterStatus") boolean filterStatus,
                               @Param("status") WorkExecutionStatus status,
                               @Param("filterReferenceType") boolean filterReferenceType,
                               @Param("referenceType") String referenceType,
                               @Param("filterReferenceId") boolean filterReferenceId,
                               @Param("referenceId") UUID referenceId,
                               Pageable pageable);
}
