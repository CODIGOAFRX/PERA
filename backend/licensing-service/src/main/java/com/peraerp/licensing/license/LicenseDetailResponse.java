package com.peraerp.licensing.license;

import java.util.List;

public record LicenseDetailResponse(
        LicenseSummaryResponse license,
        List<InstallationResponse> installations
) {
}
