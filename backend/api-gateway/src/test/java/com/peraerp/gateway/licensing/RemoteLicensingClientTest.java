package com.peraerp.gateway.licensing;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteLicensingClientTest {
    private static final String TOKEN = "remote-client-installation-token-secret";

    @Test
    void readsCurrentLicensingContractAndIgnoresOneTimeTokenField() {
        String responseBody = """
                {"valid":true,"status":"ACTIVE","nextCheckAt":"2026-08-10T12:05:00Z",
                 "graceUntil":"2026-08-10T12:30:00Z","features":["sales"],"installationToken":null}
                """;
        AtomicReference<org.springframework.web.reactive.function.client.ClientRequest> captured =
                new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(responseBody)
                    .build());
        });
        RemoteLicensingClient client = new RemoteLicensingClient(builder, properties());

        StepVerifier.create(client.validate())
                .assertNext(response -> {
                    assertThat(response.valid()).isTrue();
                    assertThat(response.status()).isEqualTo("ACTIVE");
                    assertThat(response.nextCheckAt()).isEqualTo(Instant.parse("2026-08-10T12:05:00Z"));
                    assertThat(response.companyId()).isNull();
                })
                .verifyComplete();
        assertThat(captured.get().url()).hasToString(
                "http://licensing-service:8087/public/v1/licenses/validate");
        assertThat(captured.get().headers().getFirst("X-PERA-INSTALLATION-ID"))
                .isEqualTo("gateway-installation-01");
    }

    @Test
    void remoteErrorsNeverContainInstallationToken() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse
                .create(HttpStatus.INTERNAL_SERVER_ERROR).build()));
        RemoteLicensingClient client = new RemoteLicensingClient(builder, properties());

        StepVerifier.create(client.validate())
                .expectErrorSatisfies(error -> assertThat(error.getMessage()).doesNotContain(TOKEN))
                .verify();
    }

    private LicensingProperties properties() {
        return new LicensingProperties(true, "http://licensing-service:8087", "gateway-installation-01",
                TOKEN, UUID.randomUUID().toString(), Duration.ofMinutes(5), Duration.ofSeconds(2));
    }
}
