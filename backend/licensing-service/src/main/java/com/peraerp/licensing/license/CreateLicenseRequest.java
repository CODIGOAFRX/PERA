package com.peraerp.licensing.license;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

public record CreateLicenseRequest(
        @NotBlank @Size(max = 120) String displayName,
        Instant validFrom,
        @NotNull Instant validUntil,
        @Min(0) @Max(31_536_000) long gracePeriodSeconds,
        @Min(1) @Max(10_000) int maxInstallations,
        @Min(60) @Max(604_800) long checkIntervalSeconds,
        @Size(max = 100) Set<@NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,63}") String> features
) {
}
