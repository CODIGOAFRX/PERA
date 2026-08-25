package com.peraerp.finance.accounting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class HttpSalesInvoiceClient implements SalesInvoiceClient {
    private final RestClient client;
    private final String serviceKey;

    public HttpSalesInvoiceClient(RestClient.Builder builder,
                                  @Value("${pera.services.sales-url}") String salesUrl,
                                  @Value("${pera.internal.service-key}") String serviceKey) {
        this.client = builder.baseUrl(salesUrl).build();
        this.serviceKey = serviceKey;
    }

    @Override
    public List<SalesInvoiceSnapshot> findInvoices(UUID companyId) {
        try {
            SalesInvoiceSnapshot[] response = client.get()
                    .uri(uri -> uri.path("/internal/v1/accounting/invoices")
                            .queryParam("companyId", companyId).build())
                    .header("X-PERA-SERVICE-KEY", serviceKey)
                    .retrieve().body(SalesInvoiceSnapshot[].class);
            return response == null ? List.of() : Arrays.asList(response);
        } catch (RestClientException exception) {
            // La bandeja conserva su último estado si Ventas no está disponible. La siguiente lectura reintenta.
            return List.of();
        }
    }
}
