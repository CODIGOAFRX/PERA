package com.peraerp.gateway.licensing;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
class RemoteLicensingClient implements LicensingClient {
    private final WebClient webClient;
    private final LicensingProperties properties;

    RemoteLicensingClient(WebClient.Builder webClientBuilder, LicensingProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public Mono<RemoteLicenseResponse> validate() {
        return Mono.defer(() -> properties.validationUri()
                        .<Mono<RemoteLicenseResponse>>map(uri -> webClient.post()
                                .uri(uri)
                                .header("X-PERA-INSTALLATION-ID", properties.installationId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(new ValidationRequest(properties.installationToken()))
                                .exchangeToMono(response -> {
                                    if (!response.statusCode().is2xxSuccessful()) {
                                        return response.releaseBody().then(Mono.error(
                                                new LicensingRemoteException("Licensing respondió con estado no válido.")));
                                    }
                                    return response.bodyToMono(RemoteLicenseResponse.class)
                                            .switchIfEmpty(Mono.error(new LicensingRemoteException(
                                                    "Licensing devolvió una respuesta vacía.")));
                                }))
                        .orElseGet(() -> Mono.error(new LicensingRemoteException(
                                "Licensing no está configurado."))))
                .timeout(properties.requestTimeout());
    }

    private static final class ValidationRequest {
        private final String installationToken;

        private ValidationRequest(String installationToken) {
            this.installationToken = installationToken;
        }

        public String getInstallationToken() {
            return installationToken;
        }
    }

    private static final class LicensingRemoteException extends RuntimeException {
        private LicensingRemoteException(String message) {
            super(message);
        }
    }
}
