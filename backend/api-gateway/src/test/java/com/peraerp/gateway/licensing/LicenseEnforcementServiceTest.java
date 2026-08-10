package com.peraerp.gateway.licensing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseEnforcementServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock LicensingClient client;

    private final UUID companyId = UUID.randomUUID();
    private MutableClock clock;
    private LicenseEnforcementService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new LicenseEnforcementService(properties(true, Duration.ofMinutes(5)), client, clock);
    }

    @Test
    void validDecisionIsCachedWithoutSecondRemoteCall() {
        when(client.validate()).thenReturn(Mono.just(validAt(NOW.plusSeconds(60), NOW.plusSeconds(600))));

        assertAllowed(service.authorize(companyId));
        assertAllowed(service.authorize(companyId));

        verify(client, times(1)).validate();
    }

    @Test
    void cacheExpirationTriggersRemoteRevalidation() {
        when(client.validate())
                .thenReturn(Mono.just(validAt(NOW.plusSeconds(60), NOW.plusSeconds(600))))
                .thenReturn(Mono.just(validAt(NOW.plusSeconds(180), NOW.plusSeconds(600))));

        assertAllowed(service.authorize(companyId));
        clock.advance(Duration.ofSeconds(60));
        assertAllowed(service.authorize(companyId));

        verify(client, times(2)).validate();
    }

    @Test
    void localMaximumCapsAnOverlyDistantNextCheck() {
        LicenseEnforcementService capped = new LicenseEnforcementService(
                properties(true, Duration.ofSeconds(30)), client, clock);
        when(client.validate())
                .thenReturn(Mono.just(validAt(NOW.plusSeconds(3_600), NOW.plusSeconds(7_200))))
                .thenReturn(Mono.just(validAt(NOW.plusSeconds(3_600), NOW.plusSeconds(7_200))));

        assertAllowed(capped.authorize(companyId));
        clock.advance(Duration.ofSeconds(30));
        assertAllowed(capped.authorize(companyId));

        verify(client, times(2)).validate();
    }

    @Test
    void invalidRemoteLicenseFailsClosedAndClearsPreviousGrace() {
        when(client.validate())
                .thenReturn(Mono.just(validAt(NOW.plusSeconds(10), NOW.plusSeconds(600))))
                .thenReturn(Mono.just(new RemoteLicenseResponse(false, "SUSPENDED", null,
                        NOW.plusSeconds(600), Set.of(), companyId)))
                .thenReturn(Mono.error(new IllegalStateException("remote down")));

        assertAllowed(service.authorize(companyId));
        clock.advance(Duration.ofSeconds(10));
        assertDenied(service.authorize(companyId), HttpStatus.PAYMENT_REQUIRED, "LICENSE_SUSPENDED");
        assertDenied(service.authorize(companyId), HttpStatus.PAYMENT_REQUIRED, "LICENSING_UNAVAILABLE");
    }

    @Test
    void remoteFailureUsesExactGraceDeadlineThenFailsClosed() {
        when(client.validate())
                .thenReturn(Mono.just(validAt(NOW.plusSeconds(10), NOW.plusSeconds(20))))
                .thenReturn(Mono.error(new IllegalStateException("remote down")))
                .thenReturn(Mono.error(new IllegalStateException("remote down")))
                .thenReturn(Mono.error(new IllegalStateException("remote down")));

        assertAllowed(service.authorize(companyId));
        clock.advance(Duration.ofSeconds(10));
        assertAllowed(service.authorize(companyId));
        clock.advance(Duration.ofSeconds(10));
        assertAllowed(service.authorize(companyId));
        clock.advance(Duration.ofSeconds(1));
        assertDenied(service.authorize(companyId), HttpStatus.PAYMENT_REQUIRED, "LICENSING_UNAVAILABLE");

        verify(client, times(4)).validate();
    }

    @Test
    void remoteFailureWithoutPriorValidationFailsClosed() {
        when(client.validate()).thenReturn(Mono.error(new IllegalStateException("remote down")));

        assertDenied(service.authorize(companyId), HttpStatus.PAYMENT_REQUIRED, "LICENSING_UNAVAILABLE");
    }

    @Test
    void configuredCredentialCannotAuthorizeAnotherCompany() {
        LicenseDecision decision = service.authorize(UUID.randomUUID()).block();

        assertThat(decision).isNotNull();
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.deniedStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(decision.code()).isEqualTo("LICENSE_COMPANY_MISMATCH");
        verify(client, times(0)).validate();
    }

    @Test
    void responseWithoutCompanyUsesExplicitSingleCompanyBinding() {
        when(client.validate()).thenReturn(Mono.just(new RemoteLicenseResponse(true, "ACTIVE",
                NOW.plusSeconds(60), NOW.plusSeconds(600), Set.of("sales"), null)));

        assertAllowed(service.authorize(companyId));
        LicenseDecision otherCompany = service.authorize(UUID.randomUUID()).block();

        assertThat(otherCompany).isNotNull();
        assertThat(otherCompany.allowed()).isFalse();
        assertThat(otherCompany.deniedStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(client, times(1)).validate();
    }

    @Test
    void invalidConfigurationFailsClosedWithoutContactingLicensing() {
        LicensingProperties invalid = new LicensingProperties(true, "", "", "", "",
                Duration.ofMinutes(5), Duration.ofSeconds(2));
        LicenseEnforcementService invalidService = new LicenseEnforcementService(invalid, client, clock);

        assertDenied(invalidService.authorize(companyId), HttpStatus.PAYMENT_REQUIRED,
                "LICENSING_NOT_CONFIGURED");
        verify(client, times(0)).validate();
    }

    @Test
    void concurrentRefreshesShareOneRemoteValidation() {
        Sinks.One<RemoteLicenseResponse> remote = Sinks.one();
        when(client.validate()).thenReturn(remote.asMono());

        Mono<LicenseDecision> first = service.authorize(companyId);
        Mono<LicenseDecision> second = service.authorize(companyId);

        StepVerifier.create(Mono.zip(first, second))
                .then(() -> remote.tryEmitValue(validAt(NOW.plusSeconds(60), NOW.plusSeconds(600))))
                .assertNext(pair -> {
                    assertThat(pair.getT1().allowed()).isTrue();
                    assertThat(pair.getT2().allowed()).isTrue();
                })
                .verifyComplete();
        verify(client, times(1)).validate();
    }

    @Test
    void disabledEnforcementDoesNotContactLicensing() {
        LicenseEnforcementService disabled = new LicenseEnforcementService(
                properties(false, Duration.ofMinutes(5)), client, clock);

        assertAllowed(disabled.authorize(companyId));
        verify(client, times(0)).validate();
    }

    @Test
    void propertiesNeverRenderInstallationToken() {
        LicensingProperties properties = properties(true, Duration.ofMinutes(5));

        assertThat(properties.toString()).doesNotContain("gateway-installation-token-secret");
        assertThat(properties.toString()).contains("installationToken=<redacted>");
    }

    private LicensingProperties properties(boolean enabled, Duration maximumCacheDuration) {
        return new LicensingProperties(enabled, "http://licensing-service:8087", "gateway-installation-01",
                "gateway-installation-token-secret", companyId.toString(), maximumCacheDuration,
                Duration.ofSeconds(2));
    }

    private RemoteLicenseResponse validAt(Instant nextCheckAt, Instant graceUntil) {
        return new RemoteLicenseResponse(true, "ACTIVE", nextCheckAt, graceUntil, Set.of("sales"), companyId);
    }

    private void assertAllowed(Mono<LicenseDecision> result) {
        StepVerifier.create(result)
                .assertNext(decision -> assertThat(decision.allowed()).isTrue())
                .verifyComplete();
    }

    private void assertDenied(Mono<LicenseDecision> result, HttpStatus status, String code) {
        StepVerifier.create(result)
                .assertNext(decision -> {
                    assertThat(decision.allowed()).isFalse();
                    assertThat(decision.deniedStatus()).isEqualTo(status);
                    assertThat(decision.code()).isEqualTo(code);
                })
                .verifyComplete();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
