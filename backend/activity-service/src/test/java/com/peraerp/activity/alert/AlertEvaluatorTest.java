package com.peraerp.activity.alert;

import com.peraerp.activity.audit.AuditEvent;
import com.peraerp.activity.audit.AuditOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertEvaluatorTest {
    private AlertRuleRepository ruleRepository;
    private AlertInstanceRepository alertRepository;
    private AlertEvaluator evaluator;
    private UUID companyId;
    private AuditEvent event;

    @BeforeEach
    void setUp() {
        ruleRepository = mock(AlertRuleRepository.class);
        alertRepository = mock(AlertInstanceRepository.class);
        evaluator = new AlertEvaluator(ruleRepository, alertRepository);
        companyId = UUID.randomUUID();
        event = new AuditEvent(companyId, UUID.randomUUID(), Instant.now(), "sales-service",
                "INVOICE_ISSUED", UUID.randomUUID(), "Ada", "ISSUE", "INVOICE", "FAC-42",
                AuditOutcome.SUCCESS, "corr-1", "{}");
    }

    @Test
    void createsRenderedAlertWhenSafeNestedConditionMatches() {
        AlertRule rule = rule(AlertConditionOperator.GREATER_THAN, "total", "100", 0);
        when(ruleRepository.findAllByCompanyIdAndActiveTrue(companyId)).thenReturn(List.of(rule));

        evaluator.evaluate(event, Map.of("total", 125.50));

        ArgumentCaptor<AlertInstance> captor = ArgumentCaptor.forClass(AlertInstance.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Factura FAC-42");
        assertThat(captor.getValue().getMessage()).isEqualTo("Total 125.5 emitido por Ada");
        assertThat(captor.getValue().getStatus()).isEqualTo(AlertStatus.OPEN);
    }

    @Test
    void nonNumericValueNeverSatisfiesNumericOperators() {
        AlertRule rule = rule(AlertConditionOperator.LESS_THAN, "total", "100", 0);

        assertThat(evaluator.matches(rule, event, Map.of("total", "not-a-number"))).isFalse();
    }

    @Test
    void cooldownPreventsDuplicateInboxNoise() {
        AlertRule rule = rule(AlertConditionOperator.EXISTS, "total", null, 30);
        when(ruleRepository.findAllByCompanyIdAndActiveTrue(companyId)).thenReturn(List.of(rule));
        when(alertRepository.existsByCompanyIdAndRuleIdAndDedupeKeyAndCreatedAtGreaterThanEqual(
                any(), nullable(UUID.class), any(), any())).thenReturn(true);

        evaluator.evaluate(event, Map.of("total", 125));

        verify(alertRepository, never()).save(any());
    }

    private AlertRule rule(AlertConditionOperator operator, String field, String value, int cooldown) {
        return new AlertRule(companyId, "HIGH_INVOICE", "Factura elevada", "INVOICE_ISSUED", "ISSUE",
                "INVOICE", field, operator, value, AlertSeverity.WARNING, "Factura {{resourceId}}",
                "Total {{metadata.total}} emitido por {{actorName}}", cooldown, true);
    }
}
