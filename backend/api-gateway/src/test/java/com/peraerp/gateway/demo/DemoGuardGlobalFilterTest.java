package com.peraerp.gateway.demo;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DemoGuardGlobalFilterTest {

    private final DemoGuardGlobalFilter filter = new DemoGuardGlobalFilter(new ObjectMapper());

    @Test
    void visitorCannotSendEmailsOrTouchUsers() {
        for (String path : new String[]{"/api/v1/documents/" + UUID.randomUUID() + "/email",
                "/api/v1/users/" + UUID.randomUUID(), "/api/v1/connections/mail", "/api/v1/verifactu-settings/current"}) {
            ServerWebExchange exchange = exchange(MockServerHttpRequest.post(path), true);
            AtomicInteger calls = new AtomicInteger();

            StepVerifier.create(filter.filter(exchange, ignored -> { calls.incrementAndGet(); return Mono.empty(); }))
                    .verifyComplete();

            assertThat(calls).as(path).hasValue(0);
            assertThat(exchange.getResponse().getStatusCode()).as(path).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void visitorCanWorkWithTheInventedData() {
        assertPasses(exchange(MockServerHttpRequest.post("/api/v1/documents"), true));
        assertPasses(exchange(MockServerHttpRequest.get("/api/v1/users"), true));
        assertPasses(exchange(MockServerHttpRequest.post("/api/v1/pos/tickets"), true));
    }

    @Test
    void regularTokensAreNotAffected() {
        assertPasses(exchange(MockServerHttpRequest.post("/api/v1/users"), false));
        assertPasses(exchange(MockServerHttpRequest.put("/api/v1/connections/mail"), false));
    }

    private void assertPasses(ServerWebExchange exchange) {
        AtomicInteger calls = new AtomicInteger();
        StepVerifier.create(filter.filter(exchange, ignored -> { calls.incrementAndGet(); return Mono.empty(); }))
                .verifyComplete();
        assertThat(calls).hasValue(1);
    }

    private ServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request, boolean demo) {
        Jwt.Builder jwt = Jwt.withTokenValue("jwt-value")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("company_id", UUID.randomUUID().toString())
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(60));
        if (demo) jwt.claim("demo", true);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt.build());
        return MockServerWebExchange.from(request.build())
                .mutate().principal(Mono.just(authentication)).build();
    }
}
