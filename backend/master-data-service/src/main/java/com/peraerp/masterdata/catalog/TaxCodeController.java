package com.peraerp.masterdata.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
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

@Validated
@RestController
@RequestMapping("/api/v1/tax-codes")
public class TaxCodeController {
    private final TaxCodeService service;

    public TaxCodeController(TaxCodeService service) {
        this.service = service;
    }

    @GetMapping
    Page<TaxCodeResponse> search(@RequestParam(required = false) String query,
                                 @RequestParam(required = false) @Pattern(regexp = "(?i)[A-Z]{2}") String countryCode,
                                 @RequestParam(required = false) Boolean active,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validOn,
                                 Pageable pageable) {
        return service.search(query, countryCode, active, validOn, pageable);
    }

    @GetMapping("/{id}")
    TaxCodeResponse findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TaxCodeResponse create(@Valid @RequestBody TaxCodeRequest request) { return service.create(request); }

    @PutMapping("/{id}")
    TaxCodeResponse update(@PathVariable UUID id, @Valid @RequestBody TaxCodeRequest request) {
        return service.update(id, request);
    }
}
