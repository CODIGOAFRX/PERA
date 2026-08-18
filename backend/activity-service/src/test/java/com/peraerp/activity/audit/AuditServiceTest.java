package com.peraerp.activity.audit;

import com.peraerp.activity.alert.AlertEvaluator;
import com.peraerp.activity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {
    private AuditEventRepository repository;
    private AlertEvaluator evaluator;
    private AuditService service;
    private CurrentCompanyProvider companyProvider;

    @BeforeEach
    void setUp() {
        repository = mock(AuditEventRepository.class);
        evaluator = mock(AlertEvaluator.class);
        companyProvider = mock(CurrentCompanyProvider.class);
        service = new AuditService(repository, evaluator, new AuditMetadataPolicy(),
                companyProvider, new ObjectMapper());
    }

    @Test
    void persistsAndEvaluatesNewEvent() {
        AuditEventRequest request = request(UUID.randomUUID(), UUID.randomUUID(), Instant.now());
        when(repository.findByEventId(request.eventId())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AuditEventResponse response = service.ingest(request);

        assertThat(response.eventId()).isEqualTo(request.eventId());
        assertThat(response.metadata()).containsEntry("total", 125);
        verify(evaluator).evaluate(any(AuditEvent.class), any());
    }

    @Test
    void duplicateEventIsIdempotentAndDoesNotRaiseAlertTwice() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        AuditEvent existing = new AuditEvent(companyId, eventId, Instant.now(), "sales", "CREATED", null,
                null, "CREATE", "INVOICE", "1", AuditOutcome.SUCCESS, null, "{}");
        when(repository.findByEventId(eventId)).thenReturn(Optional.of(existing));

        service.ingest(request(eventId, companyId, Instant.now()));

        verify(repository, never()).saveAndFlush(any());
        verify(evaluator, never()).evaluate(any(), any());
    }

    @Test
    void rejectsEventsTooFarInTheFuture() {
        AuditEventRequest request = request(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(301));
        when(repository.findByEventId(request.eventId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ingest(request)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void exportsTenantHistoryAsInjectionSafeCsv() {
        UUID companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        AuditEvent event = new AuditEvent(companyId, UUID.randomUUID(), Instant.parse("2026-08-10T10:00:00Z"),
                "sales", "UPDATED", null, "=2+2", "UPDATE", "QUOTE", "PRE-1",
                AuditOutcome.SUCCESS, "corr", "{\"total\":42}");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        String csv = new String(service.exportCsv(null, null, null, null, null, null, null, null, null),
                StandardCharsets.UTF_8);

        assertThat(csv).startsWith("\uFEFFevent_id").contains("\"'=2+2\"").contains("{\"\"total\"\":42}");
    }

    private AuditEventRequest request(UUID eventId, UUID companyId, Instant occurredAt) {
        return new AuditEventRequest(eventId, companyId, occurredAt, "sales-service", "INVOICE_ISSUED",
                null, "Ada", "ISSUE", "INVOICE", "FAC-1", AuditOutcome.SUCCESS, "corr",
                Map.of("total", 125));
    }
}
