package com.peraerp.sales.masterdata;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.document.DocumentLineRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesMasterDataServiceTest {
    private MasterDataClient client;
    private SalesMasterDataService service;

    @BeforeEach
    void setUp() {
        client = mock(MasterDataClient.class);
        service = new SalesMasterDataService(client, new ObjectMapper());
    }

    @Test
    void productLineIgnoresBrowserPriceCodeAndTaxAndKeepsPricingTrace() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        when(client.findProduct(productId)).thenReturn(new ProductSnapshot(productId, "SERVER-CODE", "Producto",
                new BigDecimal("50"), new BigDecimal("21"), true));
        when(client.resolvePrice(customerId, productId, new BigDecimal("3"), LocalDate.of(2026, 8, 10),
                new BigDecimal("50"), "EUR")).thenReturn(new PricingSnapshot(tariffId, "PROMO", "EUR",
                new BigDecimal("3"), new BigDecimal("4"), new BigDecimal("50"), new BigDecimal("45"),
                new BigDecimal("180"), new BigDecimal("190"),
                List.of(new PricingTraceSnapshot(1, "UNIT_MULTIPLE", tariffId, "PROMO", "Múltiplo",
                        new BigDecimal("3"), new BigDecimal("4")))));
        DocumentLineRequest request = new DocumentLineRequest(productId, "FAKE", "Descripción comercial",
                new BigDecimal("3"), new BigDecimal("0.01"), BigDecimal.ZERO, BigDecimal.ZERO);

        ResolvedDocumentLine resolved = service.resolveLine(customerId, request, LocalDate.of(2026, 8, 10), "eur");

        assertThat(resolved.productCode()).isEqualTo("SERVER-CODE");
        assertThat(resolved.taxPercentage()).isEqualByComparingTo("21");
        assertThat(resolved.billedQuantity()).isEqualByComparingTo("4");
        assertThat(resolved.pricingResolvedAmount()).isEqualByComparingTo("190");
        assertThat(resolved.pricingTraceJson()).contains("UNIT_MULTIPLE").doesNotContain("FAKE");
        verify(client).resolvePrice(customerId, productId, new BigDecimal("3"), LocalDate.of(2026, 8, 10),
                new BigDecimal("50"), "EUR");
    }

    @Test
    void freeLineKeepsExplicitCommercialValues() {
        DocumentLineRequest request = new DocumentLineRequest(null, null, "Servicio libre", BigDecimal.ONE,
                new BigDecimal("25"), new BigDecimal("10"), new BigDecimal("7"));

        ResolvedDocumentLine resolved = service.resolveLine(UUID.randomUUID(), request,
                LocalDate.of(2026, 8, 10), "EUR");

        assertThat(resolved.displayUnitPrice()).isEqualByComparingTo("25");
        assertThat(resolved.taxPercentage()).isEqualByComparingTo("7");
        assertThat(resolved.pricingResolvedAmount()).isNull();
    }

    @Test
    void productLineSnapshotsTheApplicableTaxIdentityInsteadOfTrustingTheBrowser() {
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID taxCodeId = UUID.randomUUID();
        LocalDate issueDate = LocalDate.of(2026, 8, 10);
        when(client.findProduct(productId)).thenReturn(new ProductSnapshot(productId, "P-IVA", "Producto",
                new BigDecimal("50"), new BigDecimal("10"), true, taxCodeId));
        when(client.findTaxCode(taxCodeId)).thenReturn(new TaxCodeSnapshot(taxCodeId, "ES", "IVA21",
                "IVA general", new BigDecimal("21"), LocalDate.of(2026, 1, 1), null, false, true));
        when(client.resolvePrice(customerId, productId, BigDecimal.ONE, issueDate,
                new BigDecimal("50"), "EUR")).thenReturn(new PricingSnapshot(null, null, "EUR",
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("50"), new BigDecimal("50"),
                new BigDecimal("50"), new BigDecimal("50"), List.of()));

        ResolvedDocumentLine resolved = service.resolveLine(customerId,
                new DocumentLineRequest(productId, "FALSO", "Producto", BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), issueDate, "EUR");

        assertThat(resolved.taxPercentage()).isEqualByComparingTo("21");
        assertThat(resolved.taxCodeId()).isEqualTo(taxCodeId);
        assertThat(resolved.taxCode()).isEqualTo("IVA21");
        assertThat(resolved.taxCountryCode()).isEqualTo("ES");
        assertThat(resolved.taxName()).isEqualTo("IVA general");
        assertThat(resolved.taxExempt()).isFalse();
        verify(client).findTaxCode(taxCodeId);
    }

    @Test
    void inactiveMastersCannotBeUsed() {
        UUID customerId = UUID.randomUUID();
        when(client.findCustomer(customerId)).thenReturn(new CustomerSnapshot(customerId, "C", "Cliente", false));

        assertThatThrownBy(() -> service.requireActiveCustomer(customerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactivo");
    }
}
