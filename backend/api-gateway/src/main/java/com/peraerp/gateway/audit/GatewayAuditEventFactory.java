package com.peraerp.gateway.audit;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class GatewayAuditEventFactory {
    Optional<GatewayAuditEvent> create(Map<String, Object> claims, HttpMethod method, String path,
                                       int statusCode, String correlationId, Instant occurredAt,
                                       long durationMillis) {
        UUID companyId = uuid(claims.get("company_id"));
        if (companyId == null) return Optional.empty();
        List<String> segments = java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
        int apiIndex = segments.size() >= 2 && "api".equals(segments.get(0)) && "v1".equals(segments.get(1))
                ? 2 : 0;
        String resourceSegment = segments.size() > apiIndex ? segments.get(apiIndex) : "api";
        String resourceId = segments.size() > apiIndex + 1 ? abbreviate(segments.get(apiIndex + 1), 100) : null;
        String resourceType = resourceSegment.replace('-', '_').toUpperCase(Locale.ROOT);
        String actorName = stringClaim(claims, "display_name", "username", "name", "preferred_username", "email");
        String outcome = statusCode == 401 || statusCode == 402 || statusCode == 403
                ? "DENIED"
                : statusCode >= 400 ? "FAILURE" : "SUCCESS";

        return Optional.of(new GatewayAuditEvent(UUID.randomUUID(), companyId, occurredAt, "api-gateway",
                "API_MUTATION", uuid(claims.get("sub")), abbreviate(actorName, 160), method.name(),
                abbreviate(resourceType, 100), resourceId, outcome, correlationId,
                Map.of("statusCode", statusCode, "durationMs", durationMillis, "path", abbreviate(path, 500))));
    }

    private UUID uuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String stringClaim(Map<String, Object> claims, String... names) {
        for (String name : names) {
            Object value = claims.get(name);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return null;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
