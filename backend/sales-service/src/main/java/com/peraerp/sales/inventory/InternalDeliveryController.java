package com.peraerp.sales.inventory;

import com.peraerp.sales.document.CommercialDocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Entregas de mercancía para el inventario: albaranes de venta y facturas emitidas sin albarán
 * previo. Lo consulta operations-service con la clave interna; no pasa por el gateway.
 */
@RestController
@RequestMapping("/internal/v1/inventory/deliveries")
public class InternalDeliveryController {
    static final int PAGE_SIZE = 500;

    private final CommercialDocumentRepository documents;
    private final byte[] expectedKey;

    public InternalDeliveryController(CommercialDocumentRepository documents,
                                      @Value("${pera.internal.service-key}") String serviceKey) {
        this.documents = documents;
        this.expectedKey = serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<DeliverySnapshot> findUpdatedSince(
            @RequestHeader(value = "X-PERA-SERVICE-KEY", required = false) String key,
            @RequestParam UUID companyId, @RequestParam Instant updatedSince) {
        byte[] supplied = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave interna no válida.");
        }
        return documents.findDeliveriesUpdatedSince(companyId, updatedSince, PageRequest.of(0, PAGE_SIZE)).stream()
                .map(DeliverySnapshot::from).toList();
    }
}
