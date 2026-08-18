package com.peraerp.activity.audit;

import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditMetadataPolicyTest {
    private final AuditMetadataPolicy policy = new AuditMetadataPolicy();

    @Test
    void acceptsBusinessMetadata() {
        assertThatCode(() -> policy.validate(Map.of("total", 42, "customer", Map.of("id", "c-1"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSecretsAtAnyDepth() {
        assertThatThrownBy(() -> policy.validate(Map.of("request", Map.of("authorizationToken", "secret"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("secretos");
    }
}
