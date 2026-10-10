package com.peraerp.finance.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Usuario de la sesión, para dejar constancia de quién abre y cierra una caja. */
@Component
public class CurrentUserProvider {

    public UUID requireUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            try {
                return UUID.fromString(jwt.getSubject());
            } catch (IllegalArgumentException ignored) {
                // Se responde igual que si no hubiera usuario: no se expone el contenido del token.
            }
        }
        throw new AuthenticationFailedException("El token no identifica a un usuario válido.");
    }

    /** Nombre visible del usuario para dejarlo impreso en el ticket (nombre del cajero). */
    public String displayName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            for (String claim : new String[]{"display_name", "username"}) {
                String value = jwt.getClaimAsString(claim);
                if (value != null && !value.isBlank()) {
                    return value.length() <= 160 ? value.trim() : value.trim().substring(0, 160);
                }
            }
        }
        return requireUserId().toString();
    }
}
