package com.peraerp.licensing.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentCompanyProvider {
    public UUID requireCompanyId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String companyId = jwt.getClaimAsString("company_id");
            if (companyId != null) {
                try {
                    return UUID.fromString(companyId);
                } catch (IllegalArgumentException exception) {
                    throw new AuthenticationFailedException("El token contiene una empresa activa no válida.");
                }
            }
        }
        throw new AuthenticationFailedException("El token no contiene una empresa activa.");
    }
}
