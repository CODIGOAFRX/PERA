package com.peraerp.licensing.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Defensa local de un solo nodo. Un despliegue distribuido debe aplicar además rate limiting compartido en el
 * gateway o WAF; deliberadamente no se confía en cabeceras de proxy para identificar al cliente.
 */
@Component
public class PublicLicenseRateLimitFilter extends OncePerRequestFilter {
    private static final int CLEANUP_THRESHOLD = 10_000;
    private static final String PUBLIC_PREFIX = "/public/v1/licenses/";

    private final int maximumRequests;
    private final long windowNanos;
    private final long windowSeconds;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public PublicLicenseRateLimitFilter(
            @Value("${pera.license.public-rate-limit.requests:120}") int maximumRequests,
            @Value("${pera.license.public-rate-limit.window-seconds:60}") long windowSeconds) {
        if (maximumRequests < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("La ventana y el límite público deben ser positivos.");
        }
        this.maximumRequests = maximumRequests;
        this.windowSeconds = windowSeconds;
        this.windowNanos = Duration.ofSeconds(windowSeconds).toNanos();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith(PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long now = System.nanoTime();
        String clientAddress = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        Counter counter = counters.compute(clientAddress, (ignored, current) ->
                current == null || current.expired(now, windowNanos) ? new Counter(now) : current);
        if (counter.incrementAndGet() > maximumRequests) {
            byte[] body = ("{\"valid\":false,\"status\":\"RATE_LIMITED\",\"nextCheckAt\":null," +
                    "\"graceUntil\":null,\"features\":[],\"installationToken\":null,\"companyId\":null}")
                    .getBytes(StandardCharsets.UTF_8);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("Retry-After", Long.toString(windowSeconds));
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            cleanupExpired(now);
            return;
        }
        cleanupExpired(now);
        filterChain.doFilter(request, response);
    }

    private void cleanupExpired(long now) {
        if (counters.size() > CLEANUP_THRESHOLD) {
            counters.entrySet().removeIf(entry -> entry.getValue().expired(now, windowNanos));
        }
    }

    private static final class Counter {
        private final long startedAt;
        private final AtomicInteger requests = new AtomicInteger();

        private Counter(long startedAt) {
            this.startedAt = startedAt;
        }

        private int incrementAndGet() {
            return requests.incrementAndGet();
        }

        private boolean expired(long now, long windowNanos) {
            return now - startedAt >= windowNanos;
        }
    }
}
