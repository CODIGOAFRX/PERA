package com.peraerp.activity.audit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventRequest(
        @NotNull UUID eventId,
        @NotNull UUID companyId,
        @NotNull Instant occurredAt,
        @NotBlank @Size(max = 80) String sourceService,
        @NotBlank @Size(max = 120) String eventType,
        UUID actorUserId,
        @Size(max = 160) String actorName,
        @NotBlank @Size(max = 120) String action,
        @NotBlank @Size(max = 100) String resourceType,
        @Size(max = 100) String resourceId,
        @NotNull AuditOutcome outcome,
        @Size(max = 100) String correlationId,
        Map<String, Object> metadata
) {
}
