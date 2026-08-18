package com.peraerp.activity.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AlertRuleRequest(
        @NotBlank @Size(max = 40) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") String code,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 120) String eventType,
        @Size(max = 120) String action,
        @Size(max = 100) String resourceType,
        @Size(max = 120) String conditionField,
        AlertConditionOperator conditionOperator,
        @Size(max = 240) String conditionValue,
        @NotNull AlertSeverity severity,
        @NotBlank @Size(max = 200) String titleTemplate,
        @NotBlank @Size(max = 500) String messageTemplate,
        @Min(0) @Max(525600) int cooldownMinutes,
        AlertDeliveryChannel deliveryChannel,
        boolean active
) {
}
