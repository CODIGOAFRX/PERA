package com.peraerp.finance.remittance;

import com.peraerp.finance.config.CurrentUserProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class RemittanceAuditRecorder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserProvider userProvider;

    public RemittanceAuditRecorder(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserProvider userProvider) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.userProvider = userProvider;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, Remittance remittance, RemittanceStatus previousStatus, int receiptCount) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("remittanceNumber", remittance.getRemittanceNumber());
        metadata.put("totalAmount", remittance.getTotalAmount());
        metadata.put("currencyCode", remittance.getCurrencyCode());
        metadata.put("receiptCount", receiptCount);
        metadata.put("statusFrom", previousStatus == null ? "" : previousStatus.name());
        metadata.put("statusTo", remittance.getStatus().name());
        metadata.put("bankTransmission", false);
        if (remittance.getSentDate() != null) metadata.put("sentDate", remittance.getSentDate());
        if (remittance.getSettlementDate() != null) metadata.put("settlementDate", remittance.getSettlementDate());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("companyId", remittance.getCompanyId());
        event.put("occurredAt", Instant.now());
        event.put("sourceService", "finance-service");
        event.put("eventType", "BUSINESS_ACTIVITY");
        event.put("actorUserId", userProvider.requireUserId());
        event.put("actorName", actorName());
        event.put("action", action);
        event.put("resourceType", "REMITTANCE");
        event.put("resourceId", remittance.getId().toString());
        event.put("outcome", "SUCCESS");
        event.put("correlationId", correlationId());
        event.put("metadata", metadata);
        jdbc.update("INSERT INTO remittance_audit_outbox(event_id, company_id, payload) VALUES (?, ?, ?)",
                eventId, remittance.getCompanyId(), mapper.writeValueAsString(event));
    }

    private String actorName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            for (String key : new String[]{"display_name", "username", "name", "preferred_username"}) {
                String value = jwt.getClaimAsString(key);
                if (value != null && !value.isBlank()) return value.substring(0, Math.min(value.length(), 160));
            }
        }
        return null;
    }

    private String correlationId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String value = attributes.getRequest().getHeader("X-Correlation-ID");
            if (value != null && value.matches("[A-Za-z0-9._:-]{1,100}")) return value;
        }
        return null;
    }
}
