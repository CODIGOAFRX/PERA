package com.peraerp.finance.remittance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

@Component
public class RemittanceAuditPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemittanceAuditPublisher.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RestClient client;
    private final String serviceKey;

    @Autowired
    public RemittanceAuditPublisher(JdbcTemplate jdbc, PlatformTransactionManager manager, RestClient.Builder builder,
                                   @Value("${pera.services.activity-url}") String activityUrl,
                                   @Value("${pera.internal.service-key}") String serviceKey) {
        this(jdbc, manager, createClient(builder, activityUrl), serviceKey);
    }

    RemittanceAuditPublisher(JdbcTemplate jdbc, PlatformTransactionManager manager, RestClient client, String serviceKey) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.client = client;
        this.serviceKey = serviceKey;
    }

    private static RestClient createClient(RestClient.Builder builder, String activityUrl) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        return builder.clone().baseUrl(activityUrl).requestFactory(factory).build();
    }

    @Scheduled(fixedDelayString = "${pera.remittance-audit.publish-delay:PT5S}")
    public void publishPending() {
        for (int i = 0; i < 10; i++) {
            if (!Boolean.TRUE.equals(transaction.execute(status -> publishOne()))) break;
        }
    }

    private boolean publishOne() {
        var pending = jdbc.query("""
                SELECT event_id, payload, attempts FROM remittance_audit_outbox
                WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY next_attempt_at, created_at LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (row, index) -> new Pending(row.getObject("event_id", UUID.class),
                row.getString("payload"), row.getInt("attempts")));
        if (pending.isEmpty()) return false;
        Pending event = pending.getFirst();
        try {
            client.post().uri("/internal/v1/audit-events").header("X-PERA-SERVICE-KEY", serviceKey)
                    .contentType(MediaType.APPLICATION_JSON).body(event.payload()).retrieve().toBodilessEntity();
            jdbc.update("UPDATE remittance_audit_outbox SET published_at = CURRENT_TIMESTAMP WHERE event_id = ?", event.id());
        } catch (RestClientException exception) {
            int delay = (int) Math.min(300, 5L << Math.min(event.attempts(), 6));
            jdbc.update("""
                    UPDATE remittance_audit_outbox SET attempts = attempts + 1,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second') WHERE event_id = ?
                    """, delay, event.id());
            LOGGER.warn("Auditoría de remesa {} pendiente; se reintentará en {} segundos", event.id(), delay);
        }
        return true;
    }

    private record Pending(UUID id, String payload, int attempts) { }
}
