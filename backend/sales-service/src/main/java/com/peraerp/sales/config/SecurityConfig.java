package com.peraerp.sales.config;

import com.peraerp.platform.security.JwtPermissionGrantedAuthoritiesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/documents/**").hasAuthority("documents:read")
                        .requestMatchers("/api/v1/documents/**").hasAuthority("documents:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/quotes/**").hasAuthority("quotes:read")
                        .requestMatchers("/api/v1/quotes/**").hasAuthority("quotes:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/numbering-schemes/**").hasAuthority("numbering:read")
                        .requestMatchers("/api/v1/numbering-schemes/**").hasAuthority("numbering:write")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))).build();
    }
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtPermissionGrantedAuthoritiesConverter());
        return converter;
    }
    @Bean JwtDecoder jwtDecoder(@Value("${pera.jwt.secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
