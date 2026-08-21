package com.peraerp.sales.print;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.currency.CurrentBearerTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Consulta a identity-service los datos y el logotipo de la empresa activa.
 *
 * <p>El logotipo es opcional de verdad: una empresa que no lo haya subido tiene que poder facturar
 * igual, así que un fallo al traerlo no interrumpe la impresión. Los datos, en cambio, no son
 * opcionales: una factura sin emisor no es una factura.</p>
 */
@Component
class HttpCompanyProfileClient {

    private final RestClient client;
    private final CurrentBearerTokenProvider tokenProvider;

    HttpCompanyProfileClient(RestClient.Builder builder,
                             @Value("${pera.services.identity-url:http://localhost:8081}") String identityUrl,
                             CurrentBearerTokenProvider tokenProvider) {
        this.client = builder.baseUrl(identityUrl).build();
        this.tokenProvider = tokenProvider;
    }

    CompanyProfile profile() {
        try {
            CompanyProfile response = client.get()
                    .uri("/api/v1/company-settings/current")
                    .header("Authorization", "Bearer " + tokenProvider.requireToken())
                    .retrieve()
                    .body(CompanyProfile.class);
            if (response == null || response.displayName() == null) {
                throw new BusinessRuleException("La empresa activa no tiene datos de facturación configurados.");
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudieron consultar los datos de la empresa.", exception);
        }
    }

    byte[] logo() {
        try {
            return client.get()
                    .uri("/api/v1/company-settings/current/logo")
                    .header("Authorization", "Bearer " + tokenProvider.requireToken())
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientException exception) {
            return null;
        }
    }
}
