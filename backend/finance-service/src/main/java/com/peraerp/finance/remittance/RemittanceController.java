package com.peraerp.finance.remittance;

import com.peraerp.finance.remittance.RemittanceDtos.RemittanceDateRequest;
import com.peraerp.finance.remittance.RemittanceDtos.RemittanceRequest;
import com.peraerp.finance.remittance.RemittanceDtos.RemittanceResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/remittances")
public class RemittanceController {

    private final RemittanceService service;

    public RemittanceController(RemittanceService service) {
        this.service = service;
    }

    @GetMapping
    PagedModel<RemittanceResponse> search(@RequestParam(required = false) RemittanceStatus status,
                                          Pageable pageable) {
        return new PagedModel<>(service.search(status, pageable));
    }

    @GetMapping("/{id}")
    RemittanceResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RemittanceResponse create(@Valid @RequestBody RemittanceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    RemittanceResponse update(@PathVariable UUID id, @Valid @RequestBody RemittanceRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/send")
    RemittanceResponse send(@PathVariable UUID id, @Valid @RequestBody RemittanceDateRequest request) {
        return service.send(id, request.date());
    }

    @PostMapping("/{id}/settle")
    RemittanceResponse settle(@PathVariable UUID id, @Valid @RequestBody RemittanceDateRequest request) {
        return service.settle(id, request.date());
    }

    @PostMapping("/{id}/cancel")
    RemittanceResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
