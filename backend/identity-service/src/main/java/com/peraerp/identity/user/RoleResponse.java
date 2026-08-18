package com.peraerp.identity.user;

import com.peraerp.identity.access.Permission;
import com.peraerp.identity.access.Role;

import java.util.Set;

public record RoleResponse(String code, String name, Set<String> permissions) {

    static RoleResponse from(Role role) {
        return new RoleResponse(role.getCode(), role.getName(), role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
