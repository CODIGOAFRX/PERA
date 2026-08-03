package com.peraerp.identity.auth;

import com.peraerp.identity.access.Permission;
import com.peraerp.identity.access.Role;
import com.peraerp.identity.access.UserCompany;
import com.peraerp.identity.config.JwtProperties;
import com.peraerp.identity.user.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public JwtService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public String issue(AppUser user, UserCompany membership) {
        Instant now = Instant.now();
        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        for (Role role : membership.getRoles()) {
            roles.add(role.getCode());
            role.getPermissions().stream().map(Permission::getCode).forEach(permissions::add);
        }

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ttl()))
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("display_name", user.getDisplayName())
                .claim("company_id", membership.getCompanyId().toString())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
