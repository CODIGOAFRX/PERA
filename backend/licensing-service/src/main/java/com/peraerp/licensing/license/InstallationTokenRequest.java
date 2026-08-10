package com.peraerp.licensing.license;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InstallationTokenRequest(
        @NotBlank @Size(min = 32, max = 128) String installationToken
) {
}
