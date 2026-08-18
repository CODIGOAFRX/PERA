package com.peraerp.operations.config;

import com.peraerp.platform.security.JwtPermissionGrantedAuthoritiesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/workflow-templates/**", "/api/v1/work-executions/**")
                        .hasAuthority("workflows:read")
                        .requestMatchers("/api/v1/workflow-templates/**").hasAuthority("workflows:manage")
                        .requestMatchers("/api/v1/work-executions/**").hasAuthority("workflows:execute")
                        .requestMatchers(HttpMethod.POST, "/api/v1/freight-rates/simulate")
                        .hasAuthority("freight:read")
                        .requestMatchers(HttpMethod.GET, "/api/v1/freight-rates/**")
                        .hasAuthority("freight:read")
                        .requestMatchers("/api/v1/freight-rates/**").hasAuthority("freight:write")
                        .requestMatchers(HttpMethod.POST, "/api/v1/shipments/*/freight/resolve")
                        .access(AuthorizationManagers.allOf(
                                AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority("freight:write"),
                                AuthorizationManagers.anyOf(
                                        AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority("logistics:write"),
                                        AuthorityAuthorizationManager.<RequestAuthorizationContext>hasAuthority("logistics:manage"))))
                        .requestMatchers(HttpMethod.POST, "/api/v1/shipments/*/transitions/**")
                        .hasAuthority("logistics:dispatch")
                        .requestMatchers(HttpMethod.GET, "/api/v1/carriers/**", "/api/v1/vehicles/**",
                                "/api/v1/delivery-routes/**", "/api/v1/shipments/**")
                        .hasAuthority("logistics:read")
                        .requestMatchers("/api/v1/carriers/**", "/api/v1/vehicles/**",
                                "/api/v1/delivery-routes/**", "/api/v1/shipments/**")
                        .hasAnyAuthority("logistics:write", "logistics:manage")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtPermissionGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${pera.jwt.secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
