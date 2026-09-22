package com.peraerp.sales.outbox;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;

@Component
public class DomainEventRecorder {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    public DomainEventRecorder(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository=repository; this.objectMapper=objectMapper;
    }
    private org.springframework.context.ApplicationEventPublisher publisher;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPublisher(org.springframework.context.ApplicationEventPublisher publisher) { this.publisher = publisher; }
    public record Recorded(UUID aggregateId, String eventType) {}
    public void record(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, objectMapper.writeValueAsString(payload)));
            if (publisher != null) publisher.publishEvent(new Recorded(aggregateId, eventType));
        } catch (JacksonException exception) {
            throw new BusinessRuleException("No se pudo registrar el evento de dominio " + eventType);
        }
    }
}
