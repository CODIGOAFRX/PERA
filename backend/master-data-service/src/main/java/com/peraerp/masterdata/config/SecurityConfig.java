package com.peraerp.masterdata.config;

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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/**").hasAuthority("customers:read")
                        .requestMatchers("/api/v1/customers/**").hasAuthority("customers:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/suppliers/**").hasAuthority("suppliers:read")
                        .requestMatchers("/api/v1/suppliers/**").hasAuthority("suppliers:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/product-natures/**",
                                "/api/v1/product-supertypes/**", "/api/v1/product-types/**",
                                "/api/v1/product-groups/**").hasAuthority("products:read")
                        .requestMatchers("/api/v1/products/**", "/api/v1/product-natures/**",
                                "/api/v1/product-supertypes/**", "/api/v1/product-types/**",
                                "/api/v1/product-groups/**").hasAuthority("products:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tax-codes/**").hasAuthority("taxes:read")
                        .requestMatchers("/api/v1/tax-codes/**").hasAuthority("taxes:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/tariffs/**").hasAuthority("pricing:read")
                        .requestMatchers(HttpMethod.POST, "/api/v1/pricing/**").hasAuthority("pricing:read")
                        .requestMatchers("/api/v1/tariffs/**").hasAuthority("pricing:write")
                        .requestMatchers(HttpMethod.GET, "/api/v1/packaging-types/**",
                                "/api/v1/product-packaging/**").hasAuthority("packaging:read")
                        .requestMatchers("/api/v1/packaging-types/**",
                                "/api/v1/product-packaging/**").hasAuthority("packaging:write")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt ->
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
