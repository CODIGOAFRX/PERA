package com.peraerp.identity.auth;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        UUID companyId
) {
}
