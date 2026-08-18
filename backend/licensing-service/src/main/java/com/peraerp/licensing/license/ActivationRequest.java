package com.peraerp.licensing.license;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActivationRequest(
        @NotBlank @Size(min = 32, max = 128) String activationCode,
        @NotBlank @Size(min = 8, max = 200) String installationId
) {
}
