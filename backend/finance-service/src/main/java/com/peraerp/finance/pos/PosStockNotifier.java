package com.peraerp.finance.pos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Avisa a operations-service de que hay tickets nuevos para que descuente existencias al momento.
 *
 * <p>Es solo un aviso: no lleva datos y se envía tras confirmar la transacción. Si se pierde, la
 * lectura periódica de operations-service (cada minuto) acaba recogiendo el ticket igualmente.</p>
 */
@Component
public class PosStockNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(PosStockNotifier.class);

    private final RestClient client;
    private final String serviceKey;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pos-stock-notifier");
        thread.setDaemon(true);
        return thread;
    });

    public PosStockNotifier(RestClient.Builder builder,
                            @Value("${pera.services.operations-url:http://localhost:8085}") String operationsUrl,
                            @Value("${pera.internal.service-key}") String serviceKey) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder.clone().baseUrl(operationsUrl).requestFactory(factory).build();
        this.serviceKey = serviceKey;
    }

    public void ticketsChanged(UUID companyId) {
        Runnable notify = () -> executor.execute(() -> send(companyId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notify.run();
                }
            });
        } else {
            notify.run();
        }
    }

    private void send(UUID companyId) {
        try {
            client.post().uri(uri -> uri.path("/internal/v1/inventory/pos-sync").queryParam("companyId", companyId).build())
                    .header("X-PERA-SERVICE-KEY", serviceKey)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            LOGGER.debug("Operations no recibió el aviso de tickets nuevos: {}", exception.getClass().getSimpleName());
        }
    }
}
