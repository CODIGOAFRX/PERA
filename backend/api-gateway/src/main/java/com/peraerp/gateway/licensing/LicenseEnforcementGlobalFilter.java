package com.peraerp.gateway.licensing;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Component
public class LicenseEnforcementGlobalFilter implements GlobalFilter, Ordered {
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String HEALTH_PREFIX = "/actuator/health";
    private static final String PUBLIC_LICENSE_PREFIX = "/public/v1/licenses";

    private final LicensingProperties properties;
    private final LicenseEnforcementService service;
    private final ObjectMapper objectMapper;

    public LicenseEnforcementGlobalFilter(LicensingProperties properties, LicenseEnforcementService service,
                                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enforcementEnabled() || excluded(exchange)) {
            return chain.filter(exchange);
        }

        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(this::companyId)
                .defaultIfEmpty(Optional.empty())
                .flatMap(companyId -> {
                    if (companyId.isEmpty()) {
                        return writeProblem(exchange, LicenseDecision.deny(
                                org.springframework.http.HttpStatus.FORBIDDEN, "MISSING_COMPANY_CLAIM"));
                    }
                    return service.authorize(companyId.get())
                            .onErrorReturn(LicenseDecision.deny(
                                    org.springframework.http.HttpStatus.PAYMENT_REQUIRED, "LICENSING_UNAVAILABLE"))
                            .switchIfEmpty(Mono.just(LicenseDecision.deny(
                                    org.springframework.http.HttpStatus.PAYMENT_REQUIRED, "LICENSING_UNAVAILABLE")))
                            .flatMap(decision -> decision.allowed()
                                    ? chain.filter(exchange)
                                    : writeProblem(exchange, decision));
                });
    }

    private Optional<UUID> companyId(JwtAuthenticationToken authentication) {
        Object claim = authentication.getTokenAttributes().get("company_id");
        try {
            return claim == null ? Optional.empty() : Optional.of(UUID.fromString(String.valueOf(claim)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean excluded(ServerWebExchange exchange) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return true;
        }
        String path = exchange.getRequest().getURI().getPath();
        return LOGIN_PATH.equals(path) || matchesPrefix(path, HEALTH_PREFIX) || matchesPrefix(path, PUBLIC_LICENSE_PREFIX);
    }

    private boolean matchesPrefix(String path, String prefix) {
        return prefix.equals(path) || path.startsWith(prefix + "/");
    }

    private Mono<Void> writeProblem(ServerWebExchange exchange, LicenseDecision decision) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(decision.deniedStatus(),
                "La licencia de la instalación no permite procesar esta petición.");
        problem.setTitle("Licencia requerida");
        problem.setType(URI.create("https://pera-erp.local/problems/license-required"));
        problem.setProperty("code", decision.code());

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problem);
        } catch (Exception exception) {
            body = ("{\"title\":\"Licencia requerida\",\"status\":" + decision.deniedStatus().value()
                    + ",\"detail\":\"La licencia no permite procesar esta petición.\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(decision.deniedStatus());
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        // AuditTrailGlobalFilter usa -1 y envuelve este filtro, por lo que también registra bloqueos 402/403.
        return 0;
    }
}
