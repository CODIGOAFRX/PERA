package com.peraerp.gateway.demo;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * Límites del visitante de la demo pública (token con la marca {@code demo}).
 *
 * <p>El visitante puede leer todo y trabajar con los datos inventados: crear clientes, facturar,
 * cobrar o vender en el TPV. No puede hacer nada que salga de la demo —enviar correos, guardar
 * certificados o conexiones, remitir a la AEAT o a B2Brouter— ni nada que la estropee para quien
 * venga detrás: usuarios, roles, datos y logo de la empresa, configuración de Veri*Factu y licencia.
 * Los tokens normales no llevan la marca y no se ven afectados.</p>
 */
@Component
public class DemoGuardGlobalFilter implements GlobalFilter, Ordered {

    private static final Set<HttpMethod> READ_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
    private static final List<String> BLOCKED_WRITES = List.of(
            "/api/v1/users/**",
            "/api/v1/roles/**",
            "/api/v1/companies/**",
            "/api/v1/company-settings/**",
            "/api/v1/connections/**",
            "/api/v1/verifactu-settings/**",
            "/api/v1/verifactu-records/remission",
            "/api/v1/verifactu-records/*/delivery",
            "/api/v1/documents/*/b2b",
            "/api/v1/documents/*/email/**",
            "/api/v1/documents/*/email",
            "/api/v1/quotes/*/email/**",
            "/api/v1/quotes/*/email",
            "/api/v1/licenses/**");

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    public DemoGuardGlobalFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (READ_METHODS.contains(exchange.getRequest().getMethod()) || !blocked(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }
        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(authentication -> Boolean.TRUE.equals(authentication.getTokenAttributes().get("demo")))
                .defaultIfEmpty(false)
                .flatMap(demo -> demo ? writeProblem(exchange) : chain.filter(exchange));
    }

    boolean blocked(String path) {
        return BLOCKED_WRITES.stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    private Mono<Void> writeProblem(ServerWebExchange exchange) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "En la demo no se puede cambiar esto: enviaría datos fuera o dejaría la demo distinta para el siguiente visitante.");
        problem.setTitle("No disponible en la demo");
        problem.setType(URI.create("https://pera-erp.local/problems/demo-restricted"));
        problem.setProperty("code", "DEMO_RESTRICTED");

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problem);
        } catch (Exception exception) {
            body = "{\"title\":\"No disponible en la demo\",\"status\":403}".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        exchange.getResponse().getHeaders().setCacheControl("no-store");
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        // Dentro de la auditoría (-1), que así registra también lo bloqueado; junto a la licencia (0).
        return 0;
    }
}
