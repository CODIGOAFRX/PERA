package com.peraerp.identity.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentCompanyProvider {

    public UUID requireCompanyId() {
        return requireUuidClaim("company_id", "El token no contiene una empresa activa válida.");
    }

    public UUID requireUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // The generic authentication error deliberately avoids exposing token internals.
            }
        }
        throw new AuthenticationFailedException("El token no contiene un usuario válido.");
    }

    public boolean hasRole(String roleCode) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            var roles = jwt.getClaimAsStringList("roles");
            return roles != null && roles.stream().anyMatch(roleCode::equalsIgnoreCase);
        }
        return false;
    }

    private UUID requireUuidClaim(String claimName, String message) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String value = jwt.getClaimAsString(claimName);
            if (value != null) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // The generic authentication error deliberately avoids exposing token internals.
                }
            }
        }
        throw new AuthenticationFailedException(message);
    }
}
