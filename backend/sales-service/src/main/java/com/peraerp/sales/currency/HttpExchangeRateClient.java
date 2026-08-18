package com.peraerp.sales.currency;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
class HttpExchangeRateClient implements ExchangeRateClient {

    private final RestClient client;
    private final CurrentBearerTokenProvider tokenProvider;

    HttpExchangeRateClient(RestClient.Builder builder,
                           @Value("${pera.services.finance-url:http://localhost:8084}") String financeUrl,
                           CurrentBearerTokenProvider tokenProvider) {
        this.client = builder.baseUrl(financeUrl).build();
        this.tokenProvider = tokenProvider;
    }

    @Override
    public ResolvedExchangeRate resolve(String fromCurrency, String toCurrency, LocalDate date) {
        try {
            ConversionResponse response = client.post()
                    .uri("/api/v1/currency-conversions")
                    .header("Authorization", "Bearer " + tokenProvider.requireToken())
                    .body(new ConversionRequest(BigDecimal.ONE, fromCurrency, toCurrency, date))
                    .retrieve()
                    .body(ConversionResponse.class);
            if (response == null || response.exchangeRate() == null || response.rateDate() == null) {
                throw new BusinessRuleException("El servicio financiero no devolvió una cotización válida.");
            }
            return new ResolvedExchangeRate(response.exchangeRate(), response.rateDate(), response.rateSource());
        } catch (RestClientException exception) {
            throw new BusinessRuleException("No se pudo obtener el tipo de cambio aplicable.", exception);
        }
    }

    private record ConversionRequest(BigDecimal amount, String fromCurrency, String toCurrency, LocalDate date) {
    }

    private record ConversionResponse(BigDecimal exchangeRate, LocalDate rateDate, String rateSource) {
    }
}
