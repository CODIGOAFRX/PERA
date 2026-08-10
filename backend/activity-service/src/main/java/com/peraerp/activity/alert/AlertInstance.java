package com.peraerp.activity.alert;

import com.peraerp.activity.audit.AuditEvent;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_instances")
public class AlertInstance extends CompanyScopedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false, updatable = false)
    private AlertRule rule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_event_id", nullable = false, updatable = false)
    private AuditEvent sourceEvent;

    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 300)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(nullable = false, updatable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertStatus status = AlertStatus.OPEN;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    protected AlertInstance() {
    }

    public AlertInstance(UUID companyId, AlertRule rule, AuditEvent sourceEvent, String dedupeKey,
                         AlertSeverity severity, String title, String message) {
        super(companyId);
        this.rule = rule;
        this.sourceEvent = sourceEvent;
        this.dedupeKey = dedupeKey;
        this.severity = severity;
        this.title = title;
        this.message = message;
    }

    public void acknowledge(UUID actorId, Instant now) {
        if (status != AlertStatus.OPEN) {
            throw new BusinessRuleException("Solo se pueden reconocer alertas abiertas.");
        }
        status = AlertStatus.ACKNOWLEDGED;
        acknowledgedAt = now;
        acknowledgedBy = actorId;
    }

    public void resolve(UUID actorId, Instant now) {
        if (status == AlertStatus.RESOLVED) {
            throw new BusinessRuleException("La alerta ya está resuelta.");
        }
        status = AlertStatus.RESOLVED;
        resolvedAt = now;
        resolvedBy = actorId;
    }

    public AlertRule getRule() { return rule; }
    public AuditEvent getSourceEvent() { return sourceEvent; }
    public String getDedupeKey() { return dedupeKey; }
    public AlertSeverity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public AlertStatus getStatus() { return status; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public UUID getAcknowledgedBy() { return acknowledgedBy; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }
}
