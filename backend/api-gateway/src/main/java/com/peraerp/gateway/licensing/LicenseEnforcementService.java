package com.peraerp.gateway.licensing;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
class LicenseEnforcementService {
    private static final String ACTIVE = "ACTIVE";

    private final LicensingProperties properties;
    private final LicensingClient client;
    private final Clock clock;
    private final ConcurrentMap<UUID, LicenseSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Mono<LicenseDecision>> inFlight = new ConcurrentHashMap<>();

    @Autowired
    LicenseEnforcementService(LicensingProperties properties, LicensingClient client) {
        this(properties, client, Clock.systemUTC());
    }

    LicenseEnforcementService(LicensingProperties properties, LicensingClient client, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.clock = clock;
    }

    Mono<LicenseDecision> authorize(UUID companyId) {
        if (!properties.enforcementEnabled()) {
            return Mono.just(LicenseDecision.allow("ENFORCEMENT_DISABLED"));
        }
        if (!properties.configured()) {
            return Mono.just(LicenseDecision.deny(HttpStatus.PAYMENT_REQUIRED, "LICENSING_NOT_CONFIGURED"));
        }
        UUID configuredCompanyId = properties.configuredCompanyId().orElseThrow();
        if (!configuredCompanyId.equals(companyId)) {
            return Mono.just(LicenseDecision.deny(HttpStatus.FORBIDDEN, "LICENSE_COMPANY_MISMATCH"));
        }

        Instant now = clock.instant();
        LicenseSnapshot cached = snapshots.get(companyId);
        if (cached != null && now.isBefore(cached.freshUntil())) {
            return Mono.just(LicenseDecision.allow("LICENSE_CACHE_HIT"));
        }
        return refreshSingleFlight(companyId);
    }

    private Mono<LicenseDecision> refreshSingleFlight(UUID companyId) {
        Mono<LicenseDecision> existing = inFlight.get(companyId);
        if (existing != null) {
            return existing;
        }

        Mono<LicenseDecision> candidate = Mono.defer(client::validate)
                .map(response -> applyRemoteResponse(companyId, clock.instant(), response))
                .onErrorResume(exception -> Mono.just(onRemoteFailure(companyId, clock.instant())))
                .doFinally(signal -> inFlight.remove(companyId))
                .cache();
        Mono<LicenseDecision> raced = inFlight.putIfAbsent(companyId, candidate);
        return raced == null ? candidate : raced;
    }

    private LicenseDecision applyRemoteResponse(UUID companyId, Instant now, RemoteLicenseResponse response) {
        if (response == null || response.companyId() != null && !companyId.equals(response.companyId())) {
            snapshots.remove(companyId);
            return LicenseDecision.deny(HttpStatus.FORBIDDEN, "LICENSE_COMPANY_MISMATCH");
        }
        if (!response.valid() || !ACTIVE.equals(response.status())) {
            snapshots.remove(companyId);
            return LicenseDecision.deny(HttpStatus.PAYMENT_REQUIRED, "LICENSE_" + safeStatus(response.status()));
        }
        if (response.nextCheckAt() == null || response.graceUntil() == null || now.isAfter(response.graceUntil())) {
            snapshots.remove(companyId);
            return LicenseDecision.deny(HttpStatus.PAYMENT_REQUIRED, "INVALID_LICENSE_RESPONSE");
        }

        Instant localLimit = safePlus(now);
        Instant freshUntil = earliest(response.nextCheckAt(), localLimit, response.graceUntil());
        snapshots.put(companyId, new LicenseSnapshot(freshUntil, response.graceUntil()));
        return LicenseDecision.allow("LICENSE_VALIDATED");
    }

    private LicenseDecision onRemoteFailure(UUID companyId, Instant now) {
        LicenseSnapshot previous = snapshots.get(companyId);
        if (previous != null && !now.isAfter(previous.graceUntil())) {
            return LicenseDecision.allow("REMOTE_UNAVAILABLE_WITHIN_GRACE");
        }
        snapshots.remove(companyId);
        return LicenseDecision.deny(HttpStatus.PAYMENT_REQUIRED, "LICENSING_UNAVAILABLE");
    }

    private Instant safePlus(Instant now) {
        try {
            return now.plus(properties.maximumCacheDuration());
        } catch (DateTimeException exception) {
            return Instant.MAX;
        }
    }

    private Instant earliest(Instant first, Instant second, Instant third) {
        Instant earliest = first.isBefore(second) ? first : second;
        return third.isBefore(earliest) ? third : earliest;
    }

    private String safeStatus(String status) {
        if (status == null || !status.matches("[A-Z0-9_]{1,40}")) {
            return "INVALID";
        }
        return status;
    }

    private record LicenseSnapshot(Instant freshUntil, Instant graceUntil) {
    }
}
