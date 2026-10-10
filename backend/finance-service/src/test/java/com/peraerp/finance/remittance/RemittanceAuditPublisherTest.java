package com.peraerp.finance.remittance;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RemittanceAuditPublisherTest {
    @Test
    @SuppressWarnings("unchecked")
    void retriesSameDurableEventAfterAnOutageAndMarksItOnlyAfterAcknowledgement() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        UUID id = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + id + "\"}";
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("event_id", UUID.class)).thenReturn(id);
        when(row.getString("payload")).thenReturn(payload);
        AtomicBoolean eligible = new AtomicBoolean(true);
        AtomicBoolean published = new AtomicBoolean();
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(call -> eligible.get()
                ? List.of(((RowMapper<?>) call.getArgument(1)).mapRow(row, 0)) : List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("SET published_at")) published.set(true);
            eligible.set(false);
            return 1;
        });
        RestClient.Builder builder = RestClient.builder().baseUrl("http://activity");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RemittanceAuditPublisher publisher = new RemittanceAuditPublisher(jdbc, manager, builder.build(), "test-key");
        server.expect(requestTo("http://activity/internal/v1/audit-events"))
                .andExpect(header("X-PERA-SERVICE-KEY", "test-key")).andExpect(content().string(payload))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        publisher.publishPending();
        assertThat(published).isFalse();
        verify(jdbc).update(contains("attempts = attempts + 1"), eq(5), eq(id));
        server.verify();
        server.reset();
        eligible.set(true);
        server.expect(requestTo("http://activity/internal/v1/audit-events"))
                .andExpect(content().string(payload)).andRespond(withStatus(HttpStatus.CREATED));
        publisher.publishPending();
        assertThat(published).isTrue();
        publisher.publishPending(); // ya no elegible: no hay tercer envío
        server.verify();
    }
}
