package com.peraerp.activity.alert;

import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID ruleId,
        String ruleCode,
        UUID sourceEventId,
        AlertSeverity severity,
        String title,
        String message,
        AlertStatus status,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        Instant resolvedAt,
        UUID resolvedBy,
        Instant createdAt,
        Instant updatedAt
) {
    static AlertResponse from(AlertInstance alert) {
        return new AlertResponse(alert.getId(), alert.getRule().getId(), alert.getRule().getCode(),
                alert.getSourceEvent().getEventId(), alert.getSeverity(), alert.getTitle(), alert.getMessage(),
                alert.getStatus(), alert.getAcknowledgedAt(), alert.getAcknowledgedBy(), alert.getResolvedAt(),
                alert.getResolvedBy(), alert.getCreatedAt(), alert.getUpdatedAt());
    }
}
