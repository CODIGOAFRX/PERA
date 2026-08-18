package com.peraerp.operations.logistics;

import com.peraerp.operations.config.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static com.peraerp.operations.logistics.LogisticsDtos.DeliveryRouteRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.DeliveryRouteResponse;

@RestController
@RequestMapping("/api/v1/delivery-routes")
public class DeliveryRouteController {

    private final DeliveryRouteService service;

    public DeliveryRouteController(DeliveryRouteService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<DeliveryRouteResponse> search(@RequestParam(required = false) Boolean active,
                                               @RequestParam(required = false) UUID carrierId,
                                               @RequestParam(required = false) UUID vehicleId,
                                               @RequestParam(required = false) String query,
                                               Pageable pageable) {
        return PageResponse.from(service.search(active, carrierId, vehicleId, query, pageable));
    }

    @GetMapping("/{id}")
    DeliveryRouteResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DeliveryRouteResponse create(@Valid @RequestBody DeliveryRouteRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    DeliveryRouteResponse update(@PathVariable UUID id, @Valid @RequestBody DeliveryRouteRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
