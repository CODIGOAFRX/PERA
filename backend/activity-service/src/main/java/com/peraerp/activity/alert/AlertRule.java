package com.peraerp.activity.alert;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "alert_rules")
public class AlertRule extends CompanyScopedEntity {
    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(length = 120)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "condition_field", length = 120)
    private String conditionField;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_operator", length = 30)
    private AlertConditionOperator conditionOperator;

    @Column(name = "condition_value", length = 240)
    private String conditionValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(name = "title_template", nullable = false, length = 200)
    private String titleTemplate;

    @Column(name = "message_template", nullable = false, length = 500)
    private String messageTemplate;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_channel", nullable = false, length = 20)
    private AlertDeliveryChannel deliveryChannel = AlertDeliveryChannel.IN_APP;

    @Column(nullable = false)
    private boolean active;

    protected AlertRule() {
    }

    public AlertRule(UUID companyId, String code, String name, String eventType, String action,
                     String resourceType, String conditionField, AlertConditionOperator conditionOperator,
                     String conditionValue, AlertSeverity severity, String titleTemplate,
                     String messageTemplate, int cooldownMinutes, boolean active) {
        this(companyId, code, name, eventType, action, resourceType, conditionField, conditionOperator,
                conditionValue, severity, titleTemplate, messageTemplate, cooldownMinutes,
                AlertDeliveryChannel.IN_APP, active);
    }

    public AlertRule(UUID companyId, String code, String name, String eventType, String action,
                     String resourceType, String conditionField, AlertConditionOperator conditionOperator,
                     String conditionValue, AlertSeverity severity, String titleTemplate,
                     String messageTemplate, int cooldownMinutes, AlertDeliveryChannel deliveryChannel,
                     boolean active) {
        super(companyId);
        this.code = code;
        update(name, eventType, action, resourceType, conditionField, conditionOperator, conditionValue,
                severity, titleTemplate, messageTemplate, cooldownMinutes, deliveryChannel, active);
    }

    public void update(String name, String eventType, String action, String resourceType,
                       String conditionField, AlertConditionOperator conditionOperator, String conditionValue,
                       AlertSeverity severity, String titleTemplate, String messageTemplate,
                       int cooldownMinutes, boolean active) {
        update(name, eventType, action, resourceType, conditionField, conditionOperator, conditionValue,
                severity, titleTemplate, messageTemplate, cooldownMinutes, AlertDeliveryChannel.IN_APP, active);
    }

    public void update(String name, String eventType, String action, String resourceType,
                       String conditionField, AlertConditionOperator conditionOperator, String conditionValue,
                       AlertSeverity severity, String titleTemplate, String messageTemplate,
                       int cooldownMinutes, AlertDeliveryChannel deliveryChannel, boolean active) {
        this.name = name;
        this.eventType = eventType;
        this.action = action;
        this.resourceType = resourceType;
        this.conditionField = conditionField;
        this.conditionOperator = conditionOperator;
        this.conditionValue = conditionValue;
        this.severity = severity;
        this.titleTemplate = titleTemplate;
        this.messageTemplate = messageTemplate;
        this.cooldownMinutes = cooldownMinutes;
        this.deliveryChannel = deliveryChannel;
        this.active = active;
    }

    public void deactivate() { this.active = false; }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getEventType() { return eventType; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getConditionField() { return conditionField; }
    public AlertConditionOperator getConditionOperator() { return conditionOperator; }
    public String getConditionValue() { return conditionValue; }
    public AlertSeverity getSeverity() { return severity; }
    public String getTitleTemplate() { return titleTemplate; }
    public String getMessageTemplate() { return messageTemplate; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public AlertDeliveryChannel getDeliveryChannel() { return deliveryChannel; }
    public boolean isActive() { return active; }
}
