package com.peraerp.identity.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentCompanyProvider {

    public UUID requireCompanyId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String companyId = jwt.getClaimAsString("company_id");
            if (companyId != null) {
                try {
                    return UUID.fromString(companyId);
                } catch (IllegalArgumentException ignored) {
                    // The generic authentication error deliberately avoids exposing token internals.
                }
            }
        }
        throw new AuthenticationFailedException("El token no contiene una empresa activa válida.");
    }
}
