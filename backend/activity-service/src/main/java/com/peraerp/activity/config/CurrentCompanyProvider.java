package com.peraerp.activity.config;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentCompanyProvider {
    public UUID requireCompanyId() {
        Jwt jwt = requireJwt();
        {
            String companyId = jwt.getClaimAsString("company_id");
            if (companyId != null) return UUID.fromString(companyId);
        }
        throw new BusinessRuleException("No hay una empresa activa en la sesión.");
    }

    public UUID requireUserId() {
        String subject = requireJwt().getSubject();
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new BusinessRuleException("La sesión no identifica un usuario válido.");
        }
    }

    private Jwt requireJwt() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new BusinessRuleException("No hay una sesión autenticada.");
    }
}
