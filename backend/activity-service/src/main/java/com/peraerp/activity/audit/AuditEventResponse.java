package com.peraerp.activity.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID eventId,
        UUID companyId,
        Instant occurredAt,
        String sourceService,
        String eventType,
        UUID actorUserId,
        String actorName,
        String action,
        String resourceType,
        String resourceId,
        AuditOutcome outcome,
        String correlationId,
        Map<String, Object> metadata,
        Instant ingestedAt
) {
}
