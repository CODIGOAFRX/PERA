package com.peraerp.operations.purchasing;

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

import static com.peraerp.operations.purchasing.PurchaseDtos.ConvertPurchaseDocumentRequest;
import static com.peraerp.operations.purchasing.PurchaseDtos.PurchaseDocumentRequest;
import static com.peraerp.operations.purchasing.PurchaseDtos.PurchaseDocumentResponse;

@RestController
@RequestMapping("/api/v1/purchase-documents")
public class PurchaseDocumentController {

    private final PurchaseDocumentService service;

    public PurchaseDocumentController(PurchaseDocumentService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<PurchaseDocumentResponse> search(@RequestParam(required = false) PurchaseDocumentType type,
                                                  @RequestParam(required = false) PurchaseDocumentStatus status,
                                                  @RequestParam(required = false) UUID supplierId,
                                                  @RequestParam(required = false) LocalDate fromDate,
                                                  @RequestParam(required = false) LocalDate toDate,
                                                  @RequestParam(required = false) String query,
                                                  Pageable pageable) {
        return PageResponse.from(service.search(type, status, supplierId, fromDate, toDate, query, pageable));
    }

    @GetMapping("/{id}")
    PurchaseDocumentResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseDocumentResponse create(@Valid @RequestBody PurchaseDocumentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    PurchaseDocumentResponse update(@PathVariable UUID id, @Valid @RequestBody PurchaseDocumentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/confirm")
    PurchaseDocumentResponse confirm(@PathVariable UUID id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    PurchaseDocumentResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseDocumentResponse convert(@PathVariable UUID id,
                                     @Valid @RequestBody ConvertPurchaseDocumentRequest request) {
        return service.convert(id, request);
    }
}
