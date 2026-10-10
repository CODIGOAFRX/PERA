package com.peraerp.finance.pos;

import com.peraerp.finance.pos.PosService.PosStockSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
 * Tickets del TPV para el inventario. Lo consulta operations-service con la clave interna, igual
 * que los albaranes de Ventas; no pasa por el gateway.
 */
@RestController
@RequestMapping("/internal/v1/inventory/pos-tickets")
public class InternalPosStockController {
    static final int PAGE_SIZE = 500;

    private final PosService service;
    private final byte[] expectedKey;

    public InternalPosStockController(PosService service, @Value("${pera.internal.service-key}") String serviceKey) {
        this.service = service;
        this.expectedKey = serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping
    public List<PosStockSnapshot> findUpdatedSince(
            @RequestHeader(value = "X-PERA-SERVICE-KEY", required = false) String key,
            @RequestParam UUID companyId, @RequestParam Instant updatedSince) {
        byte[] supplied = key == null ? new byte[0] : key.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedKey, supplied)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave interna no válida.");
        }
        return service.stockSnapshots(companyId, updatedSince, PAGE_SIZE);
    }
}
