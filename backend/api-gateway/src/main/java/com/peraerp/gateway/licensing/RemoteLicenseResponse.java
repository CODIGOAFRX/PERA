package com.peraerp.gateway.licensing;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

record RemoteLicenseResponse(
        boolean valid,
        String status,
        Instant nextCheckAt,
        Instant graceUntil,
        Set<String> features,
        UUID companyId
) {
}
