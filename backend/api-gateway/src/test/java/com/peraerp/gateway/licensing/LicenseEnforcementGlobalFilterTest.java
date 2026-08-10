package com.peraerp.gateway.licensing;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LicenseEnforcementGlobalFilterTest {
    private static final String SECRET_TOKEN = "gateway-installation-token-secret";

    @Test
    void disabledEnforcementBypassesServiceAndInvokesChainExactlyOnce() {
        UUID companyId = UUID.randomUUID();
        LicensingProperties properties = properties(false, companyId);
        LicenseEnforcementService service = mock(LicenseEnforcementService.class);
        LicenseEnforcementGlobalFilter filter = filter(properties, service);
        ServerWebExchange exchange = authenticatedExchange("/api/v1/documents", companyId);
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(filter.filter(exchange, ignored -> {
                    calls.incrementAndGet();
                    return Mono.empty();
                }))
                .verifyComplete();

        assertThat(calls).hasValue(1);
        verify(service, never()).authorize(companyId);
    }

    @Test
    void validDecisionInvokesChainExactlyOnce() {
        UUID companyId = UUID.randomUUID();
        LicensingProperties properties = properties(true, companyId);
        LicenseEnforcementService service = mock(LicenseEnforcementService.class);
        when(service.authorize(companyId)).thenReturn(Mono.just(LicenseDecision.allow("VALID")));
        LicenseEnforcementGlobalFilter filter = filter(properties, service);
        ServerWebExchange exchange = authenticatedExchange("/api/v1/documents", companyId);
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(filter.filter(exchange, ignored -> {
                    calls.incrementAndGet();
                    return Mono.empty();
                }))
                .verifyComplete();

        assertThat(calls).hasValue(1);
        verify(service).authorize(companyId);
    }

    @Test
    void invalidLicenseWritesProblemDetailWithoutInvokingChainOrLeakingToken() {
        UUID companyId = UUID.randomUUID();
        LicensingProperties properties = properties(true, companyId);
        LicenseEnforcementService service = mock(LicenseEnforcementService.class);
        when(service.authorize(companyId)).thenReturn(Mono.just(LicenseDecision.deny(
                HttpStatus.PAYMENT_REQUIRED, "LICENSE_SUSPENDED")));
        LicenseEnforcementGlobalFilter filter = filter(properties, service);
        MockServerWebExchange base = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/documents").build());
        ServerWebExchange exchange = base.mutate().principal(Mono.just(authentication(companyId))).build();
        AtomicInteger calls = new AtomicInteger();

        StepVerifier.create(filter.filter(exchange, ignored -> {
                    calls.incrementAndGet();
                    return Mono.empty();
                }))
                .verifyComplete();

        String body = ((MockServerHttpResponse) base.getResponse()).getBodyAsString().block();
        assertThat(calls).hasValue(0);
        assertThat(base.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(base.getResponse().getHeaders().getContentType()).hasToString("application/problem+json");
        assertThat(body).contains("LICENSE_SUSPENDED", "Licencia requerida").doesNotContain(SECRET_TOKEN);
    }

    @Test
    void healthLoginPublicLicensingAndOptionsAreExcluded() {
        UUID companyId = UUID.randomUUID();
        LicensingProperties properties = properties(true, companyId);
        LicenseEnforcementService service = mock(LicenseEnforcementService.class);
        LicenseEnforcementGlobalFilter filter = filter(properties, service);

        for (MockServerHttpRequest request : new MockServerHttpRequest[] {
                MockServerHttpRequest.get("/actuator/health/readiness").build(),
                MockServerHttpRequest.post("/api/v1/auth/login").build(),
                MockServerHttpRequest.post("/public/v1/licenses/validate").build(),
                MockServerHttpRequest.options("/api/v1/documents").build()
        }) {
            AtomicInteger calls = new AtomicInteger();
            ServerWebExchange exchange = MockServerWebExchange.from(request);
            StepVerifier.create(filter.filter(exchange, ignored -> {
                        calls.incrementAndGet();
                        return Mono.empty();
                    }))
                    .verifyComplete();
            assertThat(calls).hasValue(1);
        }
        verify(service, never()).authorize(companyId);
        assertThat(filter.getOrder()).isZero();
    }

    private LicenseEnforcementGlobalFilter filter(LicensingProperties properties,
                                                   LicenseEnforcementService service) {
        return new LicenseEnforcementGlobalFilter(properties, service, new ObjectMapper());
    }

    private LicensingProperties properties(boolean enabled, UUID companyId) {
        return new LicensingProperties(enabled, "http://licensing-service:8087", "gateway-installation-01",
                SECRET_TOKEN, companyId.toString(), Duration.ofMinutes(5), Duration.ofSeconds(2));
    }

    private ServerWebExchange authenticatedExchange(String path, UUID companyId) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build())
                .mutate().principal(Mono.just(authentication(companyId))).build();
    }

    private JwtAuthenticationToken authentication(UUID companyId) {
        Jwt jwt = Jwt.withTokenValue("jwt-value")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("company_id", companyId.toString())
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
