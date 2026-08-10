package com.peraerp.identity.config;

import com.peraerp.platform.domain.AuthenticationFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentCompanyProviderTest {

    private final CurrentCompanyProvider provider = new CurrentCompanyProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void derivesCompanyOnlyFromTheSignedJwtClaim() {
        UUID companyId = UUID.randomUUID();
        authenticateWithCompanyClaim(companyId.toString());

        assertThat(provider.requireCompanyId()).isEqualTo(companyId);
    }

    @Test
    void rejectsMissingOrMalformedCompanyClaims() {
        assertThatThrownBy(provider::requireCompanyId)
                .isInstanceOf(AuthenticationFailedException.class);

        authenticateWithCompanyClaim("not-a-uuid");
        assertThatThrownBy(provider::requireCompanyId)
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("empresa activa");
    }

    private void authenticateWithCompanyClaim(String companyId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("company_id", companyId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
