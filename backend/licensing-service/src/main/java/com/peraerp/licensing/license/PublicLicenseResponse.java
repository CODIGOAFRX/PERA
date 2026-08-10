package com.peraerp.licensing.license;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PublicLicenseResponse(
        boolean valid,
        String status,
        Instant nextCheckAt,
        Instant graceUntil,
        Set<String> features,
        String installationToken,
        UUID companyId
) {
    static PublicLicenseResponse invalid(String status) {
        return new PublicLicenseResponse(false, status, null, null, Set.of(), null, null);
    }

    static PublicLicenseResponse invalid(License license, String status) {
        return new PublicLicenseResponse(false, status, null, license.graceUntil(), Set.of(), null,
                license.getCompanyId());
    }

    static PublicLicenseResponse valid(License license, Instant now, String installationToken) {
        return new PublicLicenseResponse(true, LicenseStatus.ACTIVE.name(), license.nextCheckAt(now),
                license.graceUntil(), license.getFeatures(), installationToken, license.getCompanyId());
    }
}
