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

import static com.peraerp.operations.logistics.LogisticsDtos.VehicleRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.VehicleResponse;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<VehicleResponse> search(@RequestParam(required = false) Boolean active,
                                         @RequestParam(required = false) UUID carrierId,
                                         @RequestParam(required = false) String query,
                                         Pageable pageable) {
        return PageResponse.from(service.search(active, carrierId, query, pageable));
    }

    @GetMapping("/{id}")
    VehicleResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
