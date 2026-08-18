package com.peraerp.finance.currency;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {
    private final CurrencyService service;
    public CurrencyController(CurrencyService service) { this.service = service; }

    @GetMapping List<CurrencyResponse> findAll() { return service.findAll(); }
    @GetMapping("/{id}") CurrencyResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    CurrencyResponse create(@Valid @RequestBody CurrencyRequest request) { return service.create(request); }
    @PutMapping("/{id}")
    CurrencyResponse update(@PathVariable UUID id, @Valid @RequestBody CurrencyRequest request) {
        return service.update(id, request);
    }
}
