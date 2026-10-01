package com.peraerp.operations.salesdelivery;

import com.peraerp.operations.config.PageResponse;
import com.peraerp.operations.salesdelivery.SalesDeliveryService.SalesDeliveryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sales-deliveries")
public class SalesDeliveryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalesDeliveryController.class);

    public record PostDeliveryRequest(@NotNull UUID warehouseId) {
    }

    private final SalesDeliveryService service;

    public SalesDeliveryController(SalesDeliveryService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<SalesDeliveryResponse> search(@RequestParam(required = false) SalesDeliveryStatus status,
                                               @RequestParam(required = false) String query, Pageable pageable) {
        try {
            // Quien abre la bandeja ve las ventas recién confirmadas sin esperar a la lectura periódica.
            service.synchronizeCurrentCompany();
        } catch (RuntimeException exception) {
            // La bandeja se muestra con su último estado; la lectura periódica volverá a intentarlo.
            LOGGER.warn("No se pudieron sincronizar las salidas de venta: {}", exception.getClass().getSimpleName());
        }
        return PageResponse.from(service.search(status, query, pageable));
    }

    @PostMapping("/{id}/post")
    SalesDeliveryResponse post(@PathVariable UUID id, @Valid @RequestBody PostDeliveryRequest request) {
        return service.post(id, request.warehouseId());
    }

    @PostMapping("/{id}/dismiss")
    SalesDeliveryResponse dismiss(@PathVariable UUID id) {
        return service.dismiss(id);
    }
}
