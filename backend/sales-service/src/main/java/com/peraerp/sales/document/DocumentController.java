package com.peraerp.sales.document;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service=service; }
    @GetMapping
    Page<DocumentResponse> search(@RequestParam(required=false) DocumentType type,
                                  @RequestParam(required=false) DocumentStatus status,
                                  @RequestParam(required=false) UUID customerId,
                                  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
                                  Pageable pageable) {
        return service.search(type, status, customerId, fromDate, toDate, pageable);
    }
    @GetMapping("/{id}") DocumentResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) DocumentResponse create(@Valid @RequestBody CreateDocumentRequest request) { return service.create(request); }
    @PostMapping("/{id}/convert") @ResponseStatus(HttpStatus.CREATED) DocumentResponse convert(@PathVariable UUID id) { return service.convert(id); }
    @PatchMapping("/{id}/payment-status") DocumentResponse paymentStatus(@PathVariable UUID id, @Valid @RequestBody UpdatePaymentStatusRequest request) { return service.updatePaymentStatus(id, request.status()); }
}
