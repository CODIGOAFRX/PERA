package com.peraerp.finance.receivable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.UUID;

/**
 * Llama a Ventas con la sesión del usuario que está cobrando. No reintenta: si Ventas no responde,
 * el estado de cobro de la factura se puede corregir desde la propia factura.
 */
@Component
public class HttpInvoicePaymentNotifier implements InvoicePaymentNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpInvoicePaymentNotifier.class);

    private final RestClient client;

    public HttpInvoicePaymentNotifier(RestClient.Builder builder,
                                      @Value("${pera.services.sales-url}") String salesUrl) {
        this.client = builder.baseUrl(salesUrl).build();
    }

    @Override
    public boolean notify(UUID documentId, InvoicePaymentStatus status) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken session)) {
            return false;
        }
        try {
            client.patch().uri("/api/v1/documents/{id}/payment-status", documentId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.getToken().getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("status", status.name()))
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            LOGGER.warn("No se pudo actualizar en Ventas el estado de cobro de la factura {}: {}", documentId,
                    exception.getClass().getSimpleName());
            return false;
        }
    }
}
