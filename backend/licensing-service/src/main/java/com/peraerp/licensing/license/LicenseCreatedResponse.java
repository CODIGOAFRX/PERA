package com.peraerp.licensing.license;

public record LicenseCreatedResponse(
        LicenseSummaryResponse license,
        String activationCode
) {
}
