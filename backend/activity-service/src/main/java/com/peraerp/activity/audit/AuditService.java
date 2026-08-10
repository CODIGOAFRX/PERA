package com.peraerp.activity.audit;

import com.peraerp.activity.alert.AlertEvaluator;
import com.peraerp.activity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

@Service
public class AuditService {
    private static final int MAX_METADATA_BYTES = 65_536;

    private final AuditEventRepository repository;
    private final AlertEvaluator alertEvaluator;
    private final AuditMetadataPolicy metadataPolicy;
    private final CurrentCompanyProvider companyProvider;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, AlertEvaluator alertEvaluator,
                        AuditMetadataPolicy metadataPolicy, CurrentCompanyProvider companyProvider,
                        ObjectMapper objectMapper) {
        this.repository = repository;
        this.alertEvaluator = alertEvaluator;
        this.metadataPolicy = metadataPolicy;
        this.companyProvider = companyProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditEventResponse ingest(AuditEventRequest request) {
        var existing = repository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            if (!existing.get().getCompanyId().equals(request.companyId())) {
                throw new BusinessRuleException("El identificador de evento ya está en uso.");
            }
            return response(existing.get());
        }

        if (request.occurredAt().isAfter(Instant.now().plusSeconds(300))) {
            throw new BusinessRuleException("La fecha del evento no puede estar más de cinco minutos en el futuro.");
        }

        Map<String, Object> metadata = request.metadata() == null
                ? Map.of()
                : new LinkedHashMap<>(request.metadata());
        metadataPolicy.validate(metadata);
        String metadataJson = writeMetadata(metadata);
        AuditEvent event = new AuditEvent(request.companyId(), request.eventId(), request.occurredAt(),
                request.sourceService().trim(), request.eventType().trim(), request.actorUserId(),
                nullableTrim(request.actorName()), request.action().trim(), request.resourceType().trim(),
                nullableTrim(request.resourceId()), request.outcome(), nullableTrim(request.correlationId()),
                metadataJson);
        AuditEvent saved = repository.saveAndFlush(event);
        alertEvaluator.evaluate(saved, metadata);
        return response(saved);
    }

    @Transactional(readOnly = true)
    public AuditEventResponse findById(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        return response(repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento de auditoría", id)));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(String text, String sourceService, String eventType, String action,
                                           String resourceType, String resourceId, AuditOutcome outcome,
                                           Instant occurredFrom, Instant occurredUntil, int page, int size) {
        UUID companyId = companyProvider.requireCompanyId();
        Specification<AuditEvent> specification = specification(companyId, text, sourceService, eventType, action,
                resourceType, resourceId, outcome, occurredFrom, occurredUntil);
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200),
                Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return repository.findAll(specification, pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String text, String sourceService, String eventType, String action,
                            String resourceType, String resourceId, AuditOutcome outcome,
                            Instant occurredFrom, Instant occurredUntil) {
        UUID companyId = companyProvider.requireCompanyId();
        Specification<AuditEvent> specification = specification(companyId, text, sourceService, eventType, action,
                resourceType, resourceId, outcome, occurredFrom, occurredUntil);
        StringBuilder csv = new StringBuilder("\uFEFFevent_id,occurred_at,source_service,event_type,actor_user_id,actor,action,resource_type,resource_id,outcome,correlation_id,metadata\r\n");
        int pageNumber = 0;
        int exported = 0;
        final int maxRows = 50_000;
        Page<AuditEvent> result;
        do {
            result = repository.findAll(specification, PageRequest.of(pageNumber++, 1_000,
                    Sort.by(Sort.Direction.ASC, "occurredAt").and(Sort.by(Sort.Direction.ASC, "id"))));
            for (AuditEvent event : result.getContent()) {
                if (exported++ >= maxRows) {
                    throw new BusinessRuleException("La exportación supera el límite de 50.000 eventos; acota las fechas.");
                }
                appendCsvRow(csv, event);
            }
        } while (result.hasNext());
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Specification<AuditEvent> specification(UUID companyId, String text, String sourceService,
                                                     String eventType, String action, String resourceType,
                                                     String resourceId, AuditOutcome outcome, Instant occurredFrom,
                                                     Instant occurredUntil) {
        return (root, query, criteria) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(criteria.equal(root.get("companyId"), companyId));
            addEqualIgnoreCase(predicates, criteria, root.get("sourceService"), sourceService);
            addEqualIgnoreCase(predicates, criteria, root.get("eventType"), eventType);
            addEqualIgnoreCase(predicates, criteria, root.get("action"), action);
            addEqualIgnoreCase(predicates, criteria, root.get("resourceType"), resourceType);
            addEqualIgnoreCase(predicates, criteria, root.get("resourceId"), resourceId);
            if (outcome != null) predicates.add(criteria.equal(root.get("outcome"), outcome));
            if (occurredFrom != null) predicates.add(criteria.greaterThanOrEqualTo(root.get("occurredAt"), occurredFrom));
            if (occurredUntil != null) predicates.add(criteria.lessThanOrEqualTo(root.get("occurredAt"), occurredUntil));
            if (text != null && !text.isBlank()) {
                String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteria.or(
                        criteria.like(criteria.lower(root.get("eventType")), pattern),
                        criteria.like(criteria.lower(root.get("action")), pattern),
                        criteria.like(criteria.lower(root.get("resourceType")), pattern),
                        criteria.like(criteria.lower(root.get("resourceId")), pattern),
                        criteria.like(criteria.lower(root.get("actorName")), pattern),
                        criteria.like(criteria.lower(root.get("correlationId")), pattern)
                ));
            }
            return criteria.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void appendCsvRow(StringBuilder csv, AuditEvent event) {
        appendCsv(csv, event.getEventId().toString());
        appendCsv(csv, event.getOccurredAt().toString());
        appendCsv(csv, event.getSourceService());
        appendCsv(csv, event.getEventType());
        appendCsv(csv, event.getActorUserId() == null ? null : event.getActorUserId().toString());
        appendCsv(csv, event.getActorName());
        appendCsv(csv, event.getAction());
        appendCsv(csv, event.getResourceType());
        appendCsv(csv, event.getResourceId());
        appendCsv(csv, event.getOutcome().name());
        appendCsv(csv, event.getCorrelationId());
        appendCsv(csv, event.getMetadataJson());
        csv.setLength(csv.length() - 1);
        csv.append("\r\n");
    }

    private void appendCsv(StringBuilder csv, String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        csv.append('"').append(safe.replace("\"", "\"\"")).append("\",");
    }

    private void addEqualIgnoreCase(java.util.List<Predicate> predicates,
                                    jakarta.persistence.criteria.CriteriaBuilder criteria,
                                    jakarta.persistence.criteria.Path<String> path, String value) {
        if (value != null && !value.isBlank()) {
            predicates.add(criteria.equal(criteria.lower(path), value.trim().toLowerCase(Locale.ROOT)));
        }
    }

    private AuditEventResponse response(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getEventId(), event.getCompanyId(), event.getOccurredAt(),
                event.getSourceService(), event.getEventType(), event.getActorUserId(), event.getActorName(),
                event.getAction(), event.getResourceType(), event.getResourceId(), event.getOutcome(),
                event.getCorrelationId(), readMetadata(event.getMetadataJson()), event.getCreatedAt());
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            String json = objectMapper.writeValueAsString(metadata);
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_METADATA_BYTES) {
                throw new BusinessRuleException("Los metadatos del evento superan 64 KiB.");
            }
            return json;
        } catch (JacksonException exception) {
            throw new BusinessRuleException("Los metadatos del evento no se pueden serializar.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JacksonException exception) {
            throw new BusinessRuleException("Los metadatos almacenados no se pueden leer.", exception);
        }
    }

    private String nullableTrim(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
