package com.peraerp.sales.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class CurrentCompanyProvider {
    public UUID requireCompanyId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt && jwt.getClaimAsString("company_id") != null) {
            return UUID.fromString(jwt.getClaimAsString("company_id"));
        }
        throw new AuthenticationFailedException("El token no contiene una empresa activa.");
    }

    /** Roles of the current token, mapped by SecurityConfig to ROLE_* authorities. */
    public boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (String role : roles) if (("ROLE_" + role).equals(authority.getAuthority())) return true;
        }
        return false;
    }
}
