package com.peraerp.identity.user;

import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String username, String displayName, String email,
                           UUID companyId, Set<String> roles, boolean active) {

    static UserResponse from(com.peraerp.identity.access.UserCompany membership) {
        AppUser user = membership.getUser();
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                membership.getCompanyId(), membership.getRoles().stream()
                .map(com.peraerp.identity.access.Role::getCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                user.isActive() && membership.isActive());
    }
}
