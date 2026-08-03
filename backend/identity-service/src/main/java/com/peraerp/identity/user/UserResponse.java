package com.peraerp.identity.user;

import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String username, String displayName, String email,
                           UUID companyId, Set<String> roles, boolean active) {
}
