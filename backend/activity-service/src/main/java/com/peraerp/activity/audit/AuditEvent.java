package com.peraerp.activity.audit;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent extends CompanyScopedEntity {
    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private UUID eventId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "source_service", nullable = false, updatable = false, length = 80)
    private String sourceService;

    @Column(name = "event_type", nullable = false, updatable = false, length = 120)
    private String eventType;

    @Column(name = "actor_user_id", updatable = false)
    private UUID actorUserId;

    @Column(name = "actor_name", updatable = false, length = 160)
    private String actorName;

    @Column(nullable = false, updatable = false, length = 120)
    private String action;

    @Column(name = "resource_type", nullable = false, updatable = false, length = 100)
    private String resourceType;

    @Column(name = "resource_id", updatable = false, length = 100)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "correlation_id", updatable = false, length = 100)
    private String correlationId;

    @Column(name = "metadata_json", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String metadataJson;

    protected AuditEvent() {
    }

    public AuditEvent(UUID companyId, UUID eventId, Instant occurredAt, String sourceService,
                      String eventType, UUID actorUserId, String actorName, String action,
                      String resourceType, String resourceId, AuditOutcome outcome,
                      String correlationId, String metadataJson) {
        super(companyId);
        this.eventId = eventId;
        this.occurredAt = occurredAt;
        this.sourceService = sourceService;
        this.eventType = eventType;
        this.actorUserId = actorUserId;
        this.actorName = actorName;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.correlationId = correlationId;
        this.metadataJson = metadataJson;
    }

    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getSourceService() { return sourceService; }
    public String getEventType() { return eventType; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorName() { return actorName; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getCorrelationId() { return correlationId; }
    public String getMetadataJson() { return metadataJson; }
}
