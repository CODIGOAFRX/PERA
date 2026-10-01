package com.peraerp.finance.receivable;

import com.peraerp.finance.receivable.ReceiptDtos.CollectReceiptRequest;
import com.peraerp.finance.receivable.ReceiptDtos.IssueReceiptsRequest;
import com.peraerp.finance.receivable.ReceiptDtos.ReceiptOperationResponse;
import com.peraerp.finance.receivable.ReceiptDtos.ReceiptResponse;
import com.peraerp.finance.receivable.ReceiptDtos.ReopenReceiptRequest;
import com.peraerp.finance.receivable.ReceiptDtos.ReturnReceiptRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/receipts")
public class ReceiptController {

    private final ReceiptService service;

    public ReceiptController(ReceiptService service) {
        this.service = service;
    }

    @GetMapping
    PagedModel<ReceiptResponse> search(@RequestParam(required = false) ReceiptStatus status,
                                       @RequestParam(required = false) UUID customerId,
                                       @RequestParam(required = false) LocalDate dueFrom,
                                       @RequestParam(required = false) LocalDate dueTo,
                                       @RequestParam(defaultValue = "false") boolean available,
                                       @RequestParam(required = false) String query,
                                       Pageable pageable) {
        return new PagedModel<>(service.search(status, customerId, dueFrom, dueTo, available, query, pageable));
    }

    @GetMapping("/by-document/{documentId}")
    List<ReceiptResponse> byDocument(@PathVariable UUID documentId) {
        return service.findByDocument(documentId);
    }

    @PostMapping("/issue")
    @ResponseStatus(HttpStatus.CREATED)
    List<ReceiptResponse> issue(@Valid @RequestBody IssueReceiptsRequest request) {
        return service.issue(request);
    }

    @PostMapping("/{id}/collect")
    ReceiptOperationResponse collect(@PathVariable UUID id, @Valid @RequestBody CollectReceiptRequest request) {
        return service.collect(id, request);
    }

    @PostMapping("/{id}/return")
    ReceiptOperationResponse returnReceipt(@PathVariable UUID id, @Valid @RequestBody ReturnReceiptRequest request) {
        return service.returnReceipt(id, request);
    }

    @PostMapping("/{id}/reopen")
    ReceiptOperationResponse reopen(@PathVariable UUID id,
                                    @Valid @RequestBody(required = false) ReopenReceiptRequest request) {
        return service.reopen(id, request);
    }

    @PostMapping("/{id}/cancel")
    ReceiptOperationResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
