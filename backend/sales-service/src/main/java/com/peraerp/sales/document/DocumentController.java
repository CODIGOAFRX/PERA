package com.peraerp.sales.document;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {
    private final DocumentService service;
    public DocumentController(DocumentService service) { this.service=service; }
    @GetMapping
    Page<DocumentResponse> search(@RequestParam(required=false) String q,
                                  @RequestParam(required=false) DocumentType type,
                                  @RequestParam(required=false) DocumentStatus status,
                                  @RequestParam(required=false) UUID customerId,
                                  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                  @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
                                  Pageable pageable) {
        return service.search(q, type, status, customerId, fromDate, toDate, pageable);
    }
    @GetMapping("/{id}") DocumentResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @GetMapping("/credit-risk") CreditRiskService.Assessment creditRisk(@RequestParam UUID customerId,
                                                                        @RequestParam(defaultValue = "0") BigDecimal amount,
                                                                        @RequestParam(required = false) String currency,
                                                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.previewCreditRisk(customerId, amount, currency, date);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) DocumentResponse create(@Valid @RequestBody CreateDocumentRequest request,
                                                                              @RequestParam(defaultValue = "false") boolean riskAcknowledged) { return service.create(request, riskAcknowledged); }
    @PostMapping("/{id}/convert") @ResponseStatus(HttpStatus.CREATED) DocumentResponse convert(@PathVariable UUID id,
                                                                                            @RequestParam(defaultValue = "false") boolean riskAcknowledged) { return service.convert(id, riskAcknowledged); }
    @PostMapping("/{id}/confirm") DocumentResponse confirm(@PathVariable UUID id,
                                                           @RequestParam(defaultValue = "false") boolean riskAcknowledged) { return service.confirmDraft(id, riskAcknowledged); }
    @PatchMapping("/{id}/payment-status") DocumentResponse paymentStatus(@PathVariable UUID id, @Valid @RequestBody UpdatePaymentStatusRequest request) { return service.updatePaymentStatus(id, request.status()); }
}
