package com.peraerp.finance.currency;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {
    private final ExchangeRateService service;
    public ExchangeRateController(ExchangeRateService service) { this.service = service; }

    @GetMapping
    Page<ExchangeRateResponse> search(@RequestParam(required = false) String baseCode,
                                      @RequestParam(required = false) String quoteCode,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                      @RequestParam(required = false)
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                      @RequestParam(required = false) Boolean active, Pageable pageable) {
        return service.search(baseCode, quoteCode, fromDate, toDate, active, pageable);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    ExchangeRateResponse create(@Valid @RequestBody ExchangeRateRequest request) { return service.create(request); }
    @PutMapping("/{id}")
    ExchangeRateResponse update(@PathVariable UUID id, @Valid @RequestBody ExchangeRateUpdateRequest request) {
        return service.update(id, request);
    }
}
