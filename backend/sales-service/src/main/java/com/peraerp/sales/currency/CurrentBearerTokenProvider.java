package com.peraerp.sales.currency;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentBearerTokenProvider {
    public String requireToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            return authentication.getToken().getTokenValue();
        }
        throw new BusinessRuleException("No se puede propagar la sesión a los servicios internos.");
    }
}
