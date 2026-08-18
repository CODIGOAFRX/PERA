package com.peraerp.gateway.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
class AuditEventDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventDispatcher.class);

    private final WebClient webClient;
    private final String serviceKey;

    AuditEventDispatcher(WebClient.Builder builder,
                         @Value("${pera.services.activity-url}") String activityUrl,
                         @Value("${pera.internal.service-key}") String serviceKey) {
        this.webClient = builder.baseUrl(activityUrl).build();
        this.serviceKey = serviceKey;
    }

    Mono<Void> dispatch(GatewayAuditEvent event) {
        return webClient.post()
                .uri("/internal/v1/audit-events")
                .header("X-PERA-SERVICE-KEY", serviceKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(event)
                .retrieve()
                .toBodilessEntity()
                .then()
                .retryWhen(Retry.backoff(1, Duration.ofMillis(50)).maxBackoff(Duration.ofMillis(100)))
                .timeout(Duration.ofSeconds(1))
                .onErrorResume(exception -> {
                    LOGGER.warn("No se pudo registrar el evento de auditoría {}", event.eventId());
                    return Mono.empty();
                });
    }
}
