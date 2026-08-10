package com.peraerp.activity.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
    Optional<AuditEvent> findByEventId(UUID eventId);
    Optional<AuditEvent> findByIdAndCompanyId(UUID id, UUID companyId);

    @Query(value = "select purge_expired_audit_events(:cutoff, :batchSize)", nativeQuery = true)
    int purgeExpired(Instant cutoff, int batchSize);
}
