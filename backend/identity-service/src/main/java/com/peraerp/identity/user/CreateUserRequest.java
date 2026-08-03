package com.peraerp.identity.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(min = 10, max = 100) String password,
        @NotBlank @Size(max = 160) String displayName,
        @Email @Size(max = 180) String email,
        UUID companyId,
        @NotEmpty Set<String> roleCodes
) {
}
