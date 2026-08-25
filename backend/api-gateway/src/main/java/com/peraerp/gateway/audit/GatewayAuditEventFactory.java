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
        String resourceType = businessResource(resourceSegment);
        String action = businessAction(method, segments, apiIndex);
        String actorName = stringClaim(claims, "display_name", "username", "name", "preferred_username", "email");
        String outcome = statusCode == 401 || statusCode == 402 || statusCode == 403
                ? "DENIED"
                : statusCode >= 400 ? "FAILURE" : "SUCCESS";

        return Optional.of(new GatewayAuditEvent(UUID.randomUUID(), companyId, occurredAt, "api-gateway",
                "BUSINESS_ACTIVITY", uuid(claims.get("sub")), abbreviate(actorName, 160), action,
                abbreviate(resourceType, 100), resourceId, outcome, correlationId,
                Map.of("method", method.name(), "statusCode", statusCode, "durationMs", durationMillis,
                        "path", abbreviate(path, 500))));
    }

    private String businessAction(HttpMethod method, List<String> segments, int apiIndex) {
        String operation = segments.size() > apiIndex + 1
                ? segments.get(segments.size() - 1).replace('-', '_').toUpperCase(Locale.ROOT)
                : "";
        if (!operation.isBlank()) {
            return switch (operation) {
                case "CONVERT" -> "CONVERT";
                case "PAYMENT_STATUS" -> "UPDATE_PAYMENT_STATUS";
                case "SEND" -> "SEND";
                case "ACCEPT" -> "ACCEPT";
                case "REJECT" -> "REJECT";
                case "ISSUE" -> "ISSUE";
                case "ACKNOWLEDGE" -> "ACKNOWLEDGE";
                case "RESOLVE" -> "RESOLVE";
                case "DISPATCH" -> "DISPATCH";
                case "DELIVER" -> "DELIVER";
                case "CANCEL" -> "CANCEL";
                case "UPLOAD" -> "UPLOAD";
                case "IMPORT" -> "IMPORT";
                case "POST" -> "accounting".equals(segments.get(apiIndex)) ? "POST_ACCOUNTING" : methodAction(method);
                default -> methodAction(method);
            };
        }
        return methodAction(method);
    }

    private String methodAction(HttpMethod method) {
        if (method == HttpMethod.POST) return "CREATE";
        if (method == HttpMethod.PUT || method == HttpMethod.PATCH) return "UPDATE";
        if (method == HttpMethod.DELETE) return "DELETE";
        return "CHANGE";
    }

    private String businessResource(String segment) {
        return switch (segment) {
            case "documents" -> "SALES_DOCUMENT";
            case "quotes" -> "QUOTE";
            case "customers" -> "CUSTOMER";
            case "suppliers" -> "SUPPLIER";
            case "products" -> "PRODUCT";
            case "users" -> "USER";
            case "company-settings" -> "COMPANY_SETTINGS";
            case "verifactu-settings", "verifactu-records" -> "VERIFACTU";
            case "shipments" -> "SHIPMENT";
            case "payment-methods" -> "PAYMENT_METHOD";
            case "due-dates" -> "DUE_DATE";
            case "accounting" -> "ACCOUNTING";
            default -> segment.replace('-', '_').toUpperCase(Locale.ROOT);
        };
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
