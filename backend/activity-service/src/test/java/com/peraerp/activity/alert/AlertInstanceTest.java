package com.peraerp.activity.alert;

import com.peraerp.activity.audit.AuditEvent;
import com.peraerp.activity.audit.AuditOutcome;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertInstanceTest {
    @Test
    void supportsExplicitAcknowledgeAndResolveLifecycle() {
        UUID companyId = UUID.randomUUID();
        AlertRule rule = new AlertRule(companyId, "RULE", "Rule", "*", null, null, null, null, null,
                AlertSeverity.INFO, "Title", "Message", 0, true);
        AuditEvent event = new AuditEvent(companyId, UUID.randomUUID(), Instant.now(), "test", "TEST",
                null, null, "CREATE", "RESOURCE", "1", AuditOutcome.SUCCESS, null, "{}");
        AlertInstance alert = new AlertInstance(companyId, rule, event, "RESOURCE:1", AlertSeverity.INFO,
                "Title", "Message");
        UUID actor = UUID.randomUUID();
        Instant acknowledgedAt = Instant.now();

        alert.acknowledge(actor, acknowledgedAt);
        alert.resolve(actor, acknowledgedAt.plusSeconds(5));

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getAcknowledgedBy()).isEqualTo(actor);
        assertThat(alert.getResolvedBy()).isEqualTo(actor);
        assertThatThrownBy(() -> alert.resolve(actor, Instant.now()))
                .isInstanceOf(BusinessRuleException.class);
    }
}
