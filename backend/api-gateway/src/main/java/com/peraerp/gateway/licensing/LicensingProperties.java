package com.peraerp.gateway.licensing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public final class LicensingProperties {
    private static final String VALIDATION_PATH = "/public/v1/licenses/validate";

    private final boolean enforcementEnabled;
    private final String licensingUrl;
    private final String installationId;
    private final String installationToken;
    private final String companyId;
    private final Duration maximumCacheDuration;
    private final Duration requestTimeout;

    public LicensingProperties(
            @Value("${pera.licensing.enforcement-enabled:false}") boolean enforcementEnabled,
            @Value("${pera.licensing.licensing-url:${pera.services.licensing-url:}}") String licensingUrl,
            @Value("${pera.licensing.installation-id:}") String installationId,
            @Value("${pera.licensing.installation-token:}") String installationToken,
            @Value("${pera.licensing.company-id:}") String companyId,
            @Value("${pera.licensing.maximum-cache-duration:PT5M}") Duration maximumCacheDuration,
            @Value("${pera.licensing.request-timeout:PT2S}") Duration requestTimeout) {
        this.enforcementEnabled = enforcementEnabled;
        this.licensingUrl = normalize(licensingUrl);
        this.installationId = normalize(installationId);
        this.installationToken = normalize(installationToken);
        this.companyId = normalize(companyId);
        this.maximumCacheDuration = maximumCacheDuration;
        this.requestTimeout = requestTimeout;
    }

    public boolean enforcementEnabled() {
        return enforcementEnabled;
    }

    public boolean configured() {
        return !installationId.isBlank() && !installationToken.isBlank()
                && configuredCompanyId().isPresent() && validationUri().isPresent()
                && maximumCacheDuration != null && !maximumCacheDuration.isZero() && !maximumCacheDuration.isNegative()
                && requestTimeout != null && !requestTimeout.isZero() && !requestTimeout.isNegative();
    }

    public Optional<UUID> configuredCompanyId() {
        try {
            return companyId.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(companyId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<URI> validationUri() {
        if (licensingUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            URI base = URI.create(licensingUrl);
            if (!base.isAbsolute() || !("http".equalsIgnoreCase(base.getScheme())
                    || "https".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null) {
                return Optional.empty();
            }
            String normalizedBase = licensingUrl.endsWith("/")
                    ? licensingUrl.substring(0, licensingUrl.length() - 1) : licensingUrl;
            return Optional.of(URI.create(normalizedBase + VALIDATION_PATH));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    String installationId() {
        return installationId;
    }

    String installationToken() {
        return installationToken;
    }

    Duration maximumCacheDuration() {
        return maximumCacheDuration;
    }

    Duration requestTimeout() {
        return requestTimeout;
    }

    @Override
    public String toString() {
        return "LicensingProperties{" +
                "enforcementEnabled=" + enforcementEnabled +
                ", licensingUrlConfigured=" + !licensingUrl.isBlank() +
                ", installationIdConfigured=" + !installationId.isBlank() +
                ", installationToken=<redacted>" +
                ", companyIdConfigured=" + !companyId.isBlank() +
                ", maximumCacheDuration=" + maximumCacheDuration +
                ", requestTimeout=" + requestTimeout +
                '}';
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
