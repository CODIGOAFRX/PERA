package com.peraerp.finance.pos;

import com.peraerp.finance.config.CurrentBearerTokenProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Component
class HttpPosProductClient implements PosProductClient {

    private final RestClient client;
    private final CurrentBearerTokenProvider tokenProvider;

    HttpPosProductClient(RestClient.Builder builder,
                         @Value("${pera.services.master-data-url:http://localhost:8082}") String masterDataUrl,
                         CurrentBearerTokenProvider tokenProvider) {
        this.client = builder.clone().baseUrl(masterDataUrl).build();
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ProductSnapshot findProduct(UUID productId) {
        try {
            ProductSnapshot response = client.get().uri("/api/v1/products/{id}", productId)
                    .header("Authorization", "Bearer " + tokenProvider.requireToken())
                    .retrieve().body(ProductSnapshot.class);
            if (response == null || response.id() == null) {
                throw new BusinessRuleException("El servicio de maestros no devolvió un artículo válido.");
            }
            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResourceNotFoundException("Artículo", productId);
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudo comprobar el artículo en el catálogo.", exception);
        }
    }
}
