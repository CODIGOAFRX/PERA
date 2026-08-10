package com.peraerp.gateway.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

record GatewayAuditEvent(
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
        String outcome,
        String correlationId,
        Map<String, Object> metadata
) {
}
