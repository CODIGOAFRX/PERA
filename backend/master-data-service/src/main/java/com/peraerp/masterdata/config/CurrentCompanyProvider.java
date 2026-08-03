package com.peraerp.masterdata.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentCompanyProvider {

    public UUID requireCompanyId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof Jwt jwt) {
            String companyId = jwt.getClaimAsString("company_id");
            if (companyId != null) {
                return UUID.fromString(companyId);
            }
        }
        throw new AuthenticationFailedException("El token no contiene una empresa activa.");
    }
}
