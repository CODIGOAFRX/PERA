package com.peraerp.identity.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 30) String taxId,
        boolean active
) {
}
