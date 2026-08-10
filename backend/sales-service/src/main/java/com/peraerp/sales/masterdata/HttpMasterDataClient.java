package com.peraerp.sales.masterdata;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.currency.CurrentBearerTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
class HttpMasterDataClient implements MasterDataClient {
    private final RestClient client;
    private final CurrentBearerTokenProvider tokenProvider;

    HttpMasterDataClient(RestClient.Builder builder,
                         @Value("${pera.services.master-data-url:http://localhost:8082}") String masterDataUrl,
                         CurrentBearerTokenProvider tokenProvider) {
        this.client = builder.baseUrl(masterDataUrl).build();
        this.tokenProvider = tokenProvider;
    }

    @Override
    public CustomerSnapshot findCustomer(UUID customerId) {
        try {
            CustomerSnapshot response = client.get().uri("/api/v1/customers/{id}", customerId)
                    .header("Authorization", bearer()).retrieve().body(CustomerSnapshot.class);
            if (response == null || response.id() == null) {
                throw new BusinessRuleException("El servicio de maestros no devolvió un cliente válido.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudo validar el cliente del documento.", exception);
        }
    }

    @Override
    public ProductSnapshot findProduct(UUID productId) {
        try {
            ProductSnapshot response = client.get().uri("/api/v1/products/{id}", productId)
                    .header("Authorization", bearer()).retrieve().body(ProductSnapshot.class);
            if (response == null || response.id() == null) {
                throw new BusinessRuleException("El servicio de maestros no devolvió un producto válido.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudo validar el producto del documento.", exception);
        }
    }

    @Override
    public TaxCodeSnapshot findTaxCode(UUID taxCodeId) {
        try {
            TaxCodeSnapshot response = client.get().uri("/api/v1/tax-codes/{id}", taxCodeId)
                    .header("Authorization", bearer()).retrieve().body(TaxCodeSnapshot.class);
            if (response == null || response.id() == null) {
                throw new BusinessRuleException("El servicio de maestros no devolvió un código fiscal válido.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudo validar el código fiscal del producto.", exception);
        }
    }

    @Override
    public PricingSnapshot resolvePrice(UUID customerId, UUID productId, BigDecimal quantity,
                                        LocalDate date, BigDecimal basePrice, String currency) {
        try {
            PricingSnapshot response = client.post().uri("/api/v1/pricing/resolve")
                    .header("Authorization", bearer())
                    .body(new PricingRequest(customerId, productId, quantity, date, basePrice, currency))
                    .retrieve().body(PricingSnapshot.class);
            if (response == null || response.finalPrice() == null || response.billedQuantity() == null) {
                throw new BusinessRuleException("El servicio de tarifas no devolvió un precio válido.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudo resolver la tarifa del producto.", exception);
        }
    }

    private String bearer() { return "Bearer " + tokenProvider.requireToken(); }

    private record PricingRequest(UUID customerId, UUID productId,
                                  UUID productNatureId, UUID productSupertypeId, UUID productTypeId,
                                  UUID productGroupId, BigDecimal quantity, LocalDate date,
                                  BigDecimal basePrice, String currency) {
        private PricingRequest(UUID customerId, UUID productId, BigDecimal quantity, LocalDate date,
                               BigDecimal basePrice, String currency) {
            this(customerId, productId, null, null, null, null, quantity, date, basePrice, currency);
        }
    }
}
