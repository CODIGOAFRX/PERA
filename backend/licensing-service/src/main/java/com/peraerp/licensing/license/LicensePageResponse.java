package com.peraerp.licensing.license;

import org.springframework.data.domain.Page;

import java.util.List;

public record LicensePageResponse(
        List<LicenseSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    static LicensePageResponse from(Page<LicenseSummaryResponse> result) {
        return new LicensePageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
