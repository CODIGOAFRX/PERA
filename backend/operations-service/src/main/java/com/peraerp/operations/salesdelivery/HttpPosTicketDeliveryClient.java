package com.peraerp.operations.salesdelivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class HttpPosTicketDeliveryClient implements PosTicketDeliveryClient {

    private final RestClient client;
    private final String serviceKey;

    public HttpPosTicketDeliveryClient(@Value("${pera.services.finance-url:http://localhost:8084}") String financeUrl,
                                       @Value("${pera.internal.service-key}") String serviceKey) {
        this.client = RestClient.builder().baseUrl(financeUrl).build();
        this.serviceKey = serviceKey;
    }

    @Override
    public Optional<List<SalesDeliverySnapshot>> findUpdatedSince(UUID companyId, Instant updatedSince) {
        try {
            SalesDeliverySnapshot[] response = client.get()
                    .uri(uri -> uri.path("/internal/v1/inventory/pos-tickets")
                            .queryParam("companyId", companyId)
                            .queryParam("updatedSince", updatedSince).build())
                    .header("X-PERA-SERVICE-KEY", serviceKey)
                    .retrieve().body(SalesDeliverySnapshot[].class);
            return Optional.of(response == null ? List.of() : Arrays.asList(response));
        } catch (RestClientException exception) {
            // Si Finanzas no responde no se avanza la marca de lectura: la siguiente sincronización reintenta.
            return Optional.empty();
        }
    }
}
