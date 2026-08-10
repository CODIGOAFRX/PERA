package com.peraerp.sales.document;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private final QuoteService service;

    public QuoteController(QuoteService service) { this.service = service; }

    @GetMapping
    Page<DocumentResponse> search(@RequestParam(required = false) QuoteStatus status,
                                  @RequestParam(required = false) UUID customerId,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                  @RequestParam(required = false)
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                  Pageable pageable) {
        return service.search(status, customerId, fromDate, toDate, pageable);
    }

    @GetMapping("/{id}")
    DocumentResponse findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse create(@Valid @RequestBody CreateQuoteRequest request) { return service.create(request); }

    @PostMapping("/{id}/send")
    DocumentResponse send(@PathVariable UUID id) { return service.send(id); }

    @PostMapping("/{id}/accept")
    DocumentResponse accept(@PathVariable UUID id) { return service.accept(id); }

    @PostMapping("/{id}/reject")
    DocumentResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectQuoteRequest request) {
        return service.reject(id, request.reason());
    }

    @PostMapping("/{id}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse convert(@PathVariable UUID id) { return service.convertAccepted(id); }
}
