package com.peraerp.gateway.audit;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

@Component
public class AuditTrailGlobalFilter implements GlobalFilter, Ordered {
    private static final Set<HttpMethod> MUTATING_METHODS = Set.of(
            HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE
    );

    private final GatewayAuditEventFactory eventFactory;
    private final AuditEventDispatcher dispatcher;

    public AuditTrailGlobalFilter(GatewayAuditEventFactory eventFactory, AuditEventDispatcher dispatcher) {
        this.eventFactory = eventFactory;
        this.dispatcher = dispatcher;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpMethod method = exchange.getRequest().getMethod();
        if (!MUTATING_METHODS.contains(method)) return chain.filter(exchange);

        Instant startedAt = Instant.now();
        String correlationId = correlationId(exchange.getRequest().getHeaders().getFirst("X-Correlation-ID"));
        ServerWebExchange correlatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set("X-Correlation-ID", correlationId)))
                .build();
        correlatedExchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId);

        return correlatedExchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(authentication -> {
                    if (authentication.isEmpty()) return chain.filter(correlatedExchange);
                    Mono<Void> routed = chain.filter(correlatedExchange);
                    return routed.then(Mono.defer(() -> audit(correlatedExchange, authentication.get(), method,
                                    correlationId, startedAt, null)))
                            .onErrorResume(exception -> audit(correlatedExchange, authentication.get(), method,
                                            correlationId, startedAt, 500)
                                    .then(Mono.error(exception)));
                });
    }

    private Mono<Void> audit(ServerWebExchange exchange, JwtAuthenticationToken authentication,
                             HttpMethod method, String correlationId, Instant startedAt, Integer forcedStatus) {
        int status = forcedStatus != null
                ? forcedStatus
                : exchange.getResponse().getStatusCode() == null ? 200 : exchange.getResponse().getStatusCode().value();
        long duration = Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
        return eventFactory.create(authentication.getTokenAttributes(), method,
                        exchange.getRequest().getURI().getPath(), status, correlationId, startedAt, duration)
                .map(dispatcher::dispatch)
                .orElseGet(Mono::empty);
    }

    private String correlationId(String supplied) {
        if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,100}")) return supplied;
        return UUID.randomUUID().toString();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
