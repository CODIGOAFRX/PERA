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

import static com.peraerp.operations.logistics.LogisticsDtos.CarrierRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.CarrierResponse;

@RestController
@RequestMapping("/api/v1/carriers")
public class CarrierController {

    private final CarrierService service;

    public CarrierController(CarrierService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<CarrierResponse> search(@RequestParam(required = false) Boolean active,
                                         @RequestParam(required = false) CarrierOwnership ownership,
                                         @RequestParam(required = false) String query,
                                         Pageable pageable) {
        return PageResponse.from(service.search(active, ownership, query, pageable));
    }

    @GetMapping("/{id}")
    CarrierResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CarrierResponse create(@Valid @RequestBody CarrierRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    CarrierResponse update(@PathVariable UUID id, @Valid @RequestBody CarrierRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
