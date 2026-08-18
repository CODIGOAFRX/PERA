package com.peraerp.operations.freight;

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

import java.time.LocalDate;
import java.util.UUID;

import static com.peraerp.operations.freight.FreightDtos.FreightQuoteResponse;
import static com.peraerp.operations.freight.FreightDtos.FreightRateRequest;
import static com.peraerp.operations.freight.FreightDtos.FreightRateResponse;
import static com.peraerp.operations.freight.FreightDtos.FreightSimulationRequest;

@RestController
@RequestMapping("/api/v1/freight-rates")
public class FreightRateController {

    private final FreightRateService service;

    public FreightRateController(FreightRateService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<FreightRateResponse> search(@RequestParam(required = false) Boolean active,
                                             @RequestParam(required = false) FreightCalculationMethod method,
                                             @RequestParam(required = false) UUID routeId,
                                             @RequestParam(required = false) UUID carrierId,
                                             @RequestParam(required = false) LocalDate validOn,
                                             @RequestParam(required = false) String query,
                                             Pageable pageable) {
        return PageResponse.from(service.search(active, method, routeId, carrierId, validOn, query, pageable));
    }

    @GetMapping("/{id}")
    FreightRateResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    FreightRateResponse create(@Valid @RequestBody FreightRateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    FreightRateResponse update(@PathVariable UUID id, @Valid @RequestBody FreightRateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/simulate")
    FreightQuoteResponse simulate(@Valid @RequestBody FreightSimulationRequest request) {
        return service.simulate(request);
    }
}
