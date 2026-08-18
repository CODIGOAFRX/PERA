package com.peraerp.finance.config;
import com.peraerp.platform.security.JwtPermissionGrantedAuthoritiesConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/currencies/**", "/api/v1/exchange-rates/**")
                    .hasAuthority("currencies:read")
                .requestMatchers("/api/v1/currency-conversions/**").hasAuthority("currencies:read")
                .requestMatchers("/api/v1/currencies/**", "/api/v1/exchange-rates/**")
                    .hasAuthority("currencies:write")
                .requestMatchers(HttpMethod.GET, "/api/v1/payment-methods/**", "/api/v1/due-dates/**").hasAuthority("finance:read")
                .requestMatchers("/api/v1/payment-methods/**", "/api/v1/due-dates/**").hasAuthority("finance:write")
                .anyRequest().authenticated()).oauth2ResourceServer(s -> s.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))).build();
    }
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtPermissionGrantedAuthoritiesConverter());
        return converter;
    }
    @Bean JwtDecoder jwtDecoder(@Value("${pera.jwt.secret}") String secret) {
        var key=new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
