package com.peraerp.gateway.audit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuditEventFactoryTest {
    private final GatewayAuditEventFactory factory = new GatewayAuditEventFactory();

    @Test
    void createsTenantScopedMutationWithoutRequestBody() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        GatewayAuditEvent event = factory.create(Map.of(
                        "company_id", companyId.toString(), "sub", userId.toString(), "display_name", "Ada"),
                HttpMethod.PUT, "/api/v1/documents/123/issue", 200, "corr-1", Instant.now(), 12).orElseThrow();

        assertThat(event.companyId()).isEqualTo(companyId);
        assertThat(event.actorUserId()).isEqualTo(userId);
        assertThat(event.actorName()).isEqualTo("Ada");
        assertThat(event.resourceType()).isEqualTo("SALES_DOCUMENT");
        assertThat(event.resourceId()).isEqualTo("123");
        assertThat(event.action()).isEqualTo("ISSUE");
        assertThat(event.eventType()).isEqualTo("BUSINESS_ACTIVITY");
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.metadata()).containsOnlyKeys("method", "statusCode", "durationMs", "path");
    }

    @Test
    void mapsForbiddenRequestsToDeniedAndSkipsTokensWithoutCompany() {
        assertThat(factory.create(Map.of("sub", UUID.randomUUID().toString()), HttpMethod.DELETE,
                "/api/v1/users/1", 403, "corr", Instant.now(), 1)).isEmpty();

        GatewayAuditEvent denied = factory.create(Map.of("company_id", UUID.randomUUID().toString()),
                HttpMethod.DELETE, "/api/v1/users/1", 403, "corr", Instant.now(), 1).orElseThrow();
        assertThat(denied.outcome()).isEqualTo("DENIED");

        GatewayAuditEvent licenseDenied = factory.create(Map.of("company_id", UUID.randomUUID().toString()),
                HttpMethod.POST, "/api/v1/documents", 402, "corr", Instant.now(), 1).orElseThrow();
        assertThat(licenseDenied.outcome()).isEqualTo("DENIED");
    }

    @Test
    void classifiesBulkMasterDataImportsAsImports() {
        Map<String, Object> claims = Map.of("company_id", UUID.randomUUID().toString());

        GatewayAuditEvent suppliers = factory.create(claims, HttpMethod.POST,
                "/api/v1/suppliers/import", 200, "corr-suppliers", Instant.now(), 20).orElseThrow();
        GatewayAuditEvent products = factory.create(claims, HttpMethod.POST,
                "/api/v1/products/import", 200, "corr-products", Instant.now(), 20).orElseThrow();

        assertThat(suppliers.action()).isEqualTo("IMPORT");
        assertThat(suppliers.resourceType()).isEqualTo("SUPPLIER");
        assertThat(products.action()).isEqualTo("IMPORT");
        assertThat(products.resourceType()).isEqualTo("PRODUCT");
    }
}
