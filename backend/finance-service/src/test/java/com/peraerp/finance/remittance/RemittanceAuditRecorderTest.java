package com.peraerp.finance.remittance;

import com.peraerp.finance.config.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RemittanceAuditRecorderTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void storesTenantActorAndBusinessSnapshotWithoutCredentialsOrBankAccount() {
        UUID company = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("secret-session").header("alg", "HS256").subject(user.toString())
                .claim("display_name", "Ana").claim("company_id", company.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RemittanceAuditRecorder recorder = new RemittanceAuditRecorder(jdbc, new ObjectMapper(), new CurrentUserProvider());
        Remittance remittance = new Remittance(company, "REM-2026-0002", "private-bank-account", "EUR", LocalDate.now(), null);
        ReflectionTestUtils.setField(remittance, "id", UUID.randomUUID());
        remittance.updateDraft("private-bank-account", null, new BigDecimal("847"));
        remittance.send(LocalDate.now());
        recorder.record("SEND", remittance, RemittanceStatus.DRAFT, 3);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(contains("INSERT INTO remittance_audit_outbox"), any(UUID.class), eq(company), payload.capture());
        Map<?, ?> event = new ObjectMapper().readValue(payload.getValue(), Map.class);
        assertThat(event.get("actorUserId")).isEqualTo(user.toString());
        assertThat(event.get("companyId")).isEqualTo(company.toString());
        assertThat(event.get("actorName")).isEqualTo("Ana");
        assertThat(event.get("resourceId")).isEqualTo(remittance.getId().toString());
        assertThat(payload.getValue()).contains("REM-2026-0002", "DRAFT", "SENT").doesNotContain("secret-session", "private-bank-account");
    }
}
