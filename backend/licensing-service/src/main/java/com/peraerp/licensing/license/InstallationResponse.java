package com.peraerp.licensing.license;

import java.time.Instant;
import java.util.UUID;

public record InstallationResponse(
        UUID id,
        InstallationStatus status,
        Instant tokenIssuedAt,
        Instant activatedAt,
        Instant lastValidatedAt,
        Instant revokedAt
) {
    static InstallationResponse from(LicenseInstallation installation) {
        return new InstallationResponse(installation.getId(), installation.getStatus(),
                installation.getTokenIssuedAt(), installation.getActivatedAt(), installation.getLastValidatedAt(),
                installation.getRevokedAt());
    }
}
