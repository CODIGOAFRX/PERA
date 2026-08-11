package com.peraerp.identity.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank @Size(max = 160) String displayName,
        @Email @Size(max = 180) String email,
        @Size(min = 10, max = 100) String password,
        @NotEmpty Set<String> roleCodes,
        boolean active
) {
}
