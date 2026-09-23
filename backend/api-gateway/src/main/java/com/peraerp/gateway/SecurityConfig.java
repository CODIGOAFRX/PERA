package com.peraerp.gateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
@Configuration
public class SecurityConfig {
    @Bean SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http){
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange->exchange
                        .pathMatchers("/api/v1/auth/login", "/public/v1/licenses/**", "/actuator/health/**").permitAll()
                        // Operational endpoints (metrics, config) are for internal scraping, never for end users.
                        .pathMatchers("/actuator/**").denyAll()
                        .anyExchange().authenticated())
                .headers(headers->headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame->frame.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .referrerPolicy(referrer->referrer.policy(org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.NO_REFERRER)))
                .oauth2ResourceServer(server->server.jwt(Customizer.withDefaults())).build();
    }
    @Bean ReactiveJwtDecoder reactiveJwtDecoder(@Value("${pera.jwt.secret}") String secret){
        var key=new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
