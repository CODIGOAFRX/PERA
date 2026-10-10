package com.peraerp.operations.salesdelivery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Aviso del TPV: hay tickets nuevos. Se sincroniza en el momento para que el almacén no tenga que
 * esperar a la lectura periódica. No recibe datos; los lee del punto interno de Finanzas.
 */
@RestController
@RequestMapping("/internal/v1/inventory/pos-sync")
public class InternalInventorySyncController {

    private final SalesDeliveryService service;
    private final byte[] expectedKey;

    public InternalInventorySyncController(SalesDeliveryService service,
                                           @Value("${pera.internal.service-key}") String serviceKey) {
        this.service = service;
        this.expectedKey = serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void synchronize(@RequestHeader(value = "X-PERA-SERVICE-KEY", required = false) String key,
                            @RequestParam UUID companyId) {
        byte[] supplied = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave interna no válida.");
        }
        service.synchronize(companyId);
    }
}
