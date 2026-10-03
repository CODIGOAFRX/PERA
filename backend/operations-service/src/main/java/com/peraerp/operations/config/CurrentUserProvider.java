package com.peraerp.operations.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Usuario de la sesión, para dejar constancia de quién registra una reclamación, un comentario o una cita. */
@Component
public class CurrentUserProvider {

    public record CurrentUser(UUID id, String name) {
    }

    public CurrentUser requireUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            try {
                UUID id = UUID.fromString(jwt.getSubject());
                String name = firstPresent(jwt.getClaimAsString("display_name"), jwt.getClaimAsString("username"));
                return new CurrentUser(id, name == null ? id.toString() : abbreviate(name));
            } catch (IllegalArgumentException ignored) {
                // Se responde igual que si no hubiera usuario: no se expone el contenido del token.
            }
        }
        throw new AuthenticationFailedException("El token no identifica a un usuario válido.");
    }

    private static String firstPresent(String first, String second) {
        return first != null && !first.isBlank() ? first.trim() : second == null || second.isBlank() ? null : second.trim();
    }

    private static String abbreviate(String value) {
        return value.length() <= 160 ? value : value.substring(0, 160);
    }
}
