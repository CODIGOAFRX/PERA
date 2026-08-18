package com.peraerp.activity.alert;

import java.time.Instant;
import java.util.UUID;

public record AlertRuleResponse(
        UUID id,
        String code,
        String name,
        String eventType,
        String action,
        String resourceType,
        String conditionField,
        AlertConditionOperator conditionOperator,
        String conditionValue,
        AlertSeverity severity,
        String titleTemplate,
        String messageTemplate,
        int cooldownMinutes,
        AlertDeliveryChannel deliveryChannel,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    static AlertRuleResponse from(AlertRule rule) {
        return new AlertRuleResponse(rule.getId(), rule.getCode(), rule.getName(), rule.getEventType(),
                rule.getAction(), rule.getResourceType(), rule.getConditionField(), rule.getConditionOperator(),
                rule.getConditionValue(), rule.getSeverity(), rule.getTitleTemplate(), rule.getMessageTemplate(),
                rule.getCooldownMinutes(), rule.getDeliveryChannel(), rule.isActive(), rule.getCreatedAt(),
                rule.getUpdatedAt());
    }
}
