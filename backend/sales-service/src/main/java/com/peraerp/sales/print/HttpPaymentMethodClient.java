package com.peraerp.sales.print;

import com.peraerp.sales.currency.CurrentBearerTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * Traduce el identificador de forma de pago que guarda la factura al nombre que se imprime.
 *
 * <p>El documento solo guarda el identificador, y el catálogo vive en finance-service. La consulta
 * es del listado entero porque es lo único que ese servicio expone; son cuatro o cinco filas y se
 * pide una vez por factura impresa.</p>
 *
 * <p>Nunca interrumpe la impresión. Quien imprime una factura puede no tener permiso sobre el
 * catálogo financiero, y quedarse sin poder emitir por no poder leer un rótulo sería absurdo: en
 * ese caso la factura sale sin la forma de pago, que es un dato informativo.</p>
 */
@Component
class HttpPaymentMethodClient {

    private final RestClient client;
    private final CurrentBearerTokenProvider tokenProvider;

    HttpPaymentMethodClient(RestClient.Builder builder,
                            @Value("${pera.services.finance-url:http://localhost:8084}") String financeUrl,
                            CurrentBearerTokenProvider tokenProvider) {
        this.client = builder.baseUrl(financeUrl).build();
        this.tokenProvider = tokenProvider;
    }

    String nameOf(UUID paymentMethodId) {
        if (paymentMethodId == null) {
            return null;
        }
        try {
            List<PaymentMethodName> methods = client.get()
                    .uri("/api/v1/payment-methods")
                    .header("Authorization", "Bearer " + tokenProvider.requireToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PaymentMethodName>>() { });
            if (methods == null) {
                return null;
            }
            return methods.stream()
                    .filter(method -> paymentMethodId.equals(method.id()))
                    .map(PaymentMethodName::name)
                    .findFirst()
                    .orElse(null);
        } catch (RestClientException exception) {
            return null;
        }
    }

    private record PaymentMethodName(UUID id, String name) {
    }
}
