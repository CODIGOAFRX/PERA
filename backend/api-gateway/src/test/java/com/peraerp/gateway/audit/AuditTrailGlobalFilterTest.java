package com.peraerp.gateway.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditTrailGlobalFilterTest {
    @Test
    void routesMutationExactlyOnceAndDispatchesOneAuditEvent() {
        AuditEventDispatcher dispatcher = mock(AuditEventDispatcher.class);
        when(dispatcher.dispatch(any())).thenReturn(Mono.empty());
        AuditTrailGlobalFilter filter = new AuditTrailGlobalFilter(new GatewayAuditEventFactory(), dispatcher);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("company_id", UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        ServerWebExchange exchange = MockServerWebExchange
                .from(MockServerHttpRequest.post("/api/v1/documents").build())
                .mutate()
                .principal(Mono.just(authentication))
                .build();
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(filter.filter(exchange, ignored -> {
                    calls.incrementAndGet();
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.CREATED);
                    return Mono.empty();
                }))
                .verifyComplete();

        org.assertj.core.api.Assertions.assertThat(calls).hasValue(1);
        verify(dispatcher).dispatch(any());
    }
}
