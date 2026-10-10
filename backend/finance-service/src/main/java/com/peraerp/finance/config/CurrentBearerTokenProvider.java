package com.peraerp.finance.config;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Token de la petición en curso, para consultar maestros con los permisos del propio usuario. */
@Component
public class CurrentBearerTokenProvider {
    public String requireToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            return authentication.getToken().getTokenValue();
        }
        throw new BusinessRuleException("No se puede propagar la sesión a los servicios internos.");
    }
}
