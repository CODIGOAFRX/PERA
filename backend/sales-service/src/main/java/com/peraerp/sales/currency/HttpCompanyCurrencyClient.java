package com.peraerp.sales.currency;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class HttpCompanyCurrencyClient implements CompanyCurrencyClient {

    private final RestClient client;
    private final CurrentBearerTokenProvider tokenProvider;

    HttpCompanyCurrencyClient(RestClient.Builder builder,
                              @Value("${pera.services.identity-url:http://localhost:8081}") String identityUrl,
                              CurrentBearerTokenProvider tokenProvider) {
        this.client = builder.baseUrl(identityUrl).build();
        this.tokenProvider = tokenProvider;
    }

    @Override
    public String currentBaseCurrency() {
        try {
            CompanySettingsCurrency response = client.get()
                    .uri("/api/v1/company-settings/current")
                    .header("Authorization", "Bearer " + tokenProvider.requireToken())
                    .retrieve()
                    .body(CompanySettingsCurrency.class);
            if (response == null || response.baseCurrency() == null) {
                throw new BusinessRuleException("La empresa activa no tiene moneda base configurada.");
            }
            return response.baseCurrency();
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudo consultar la moneda base de la empresa.", exception);
        }
    }

    private record CompanySettingsCurrency(String baseCurrency) {
    }
}
