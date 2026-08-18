package com.peraerp.licensing.license;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record LicenseSummaryResponse(
        UUID id,
        UUID companyId,
        String displayName,
        LicenseStatus status,
        Instant validFrom,
        Instant validUntil,
        Instant graceUntil,
        long gracePeriodSeconds,
        int maxInstallations,
        int activeInstallations,
        long checkIntervalSeconds,
        Set<String> features,
        Instant firstActivatedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
