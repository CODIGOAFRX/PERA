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
}
