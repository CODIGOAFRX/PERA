package com.peraerp.finance.accounting;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounting")
public class AccountingController {
    private final AccountingService service;

    public AccountingController(AccountingService service) { this.service = service; }

    @GetMapping("/accounts") List<AccountingAccountResponse> accounts(@RequestParam(required = false) String q) {
        return service.findAccounts(q);
    }
    @GetMapping("/inbox") List<AccountingInboxResponse> inbox() { return service.findPending(); }
    @GetMapping("/inbox/count") AccountingCountResponse count() { return service.pendingCount(); }
    @GetMapping("/entries") List<AccountingEntryResponse> entries() { return service.findEntries(); }
    @PostMapping("/inbox/{id}/post") @ResponseStatus(HttpStatus.CREATED)
    AccountingEntryResponse post(@PathVariable UUID id, @Valid @RequestBody AccountingEntryRequest request) {
        return service.postInboxItem(id, request);
    }
    @PostMapping("/entries") @ResponseStatus(HttpStatus.CREATED)
    AccountingEntryResponse manual(@Valid @RequestBody AccountingEntryRequest request) {
        return service.postManual(request);
    }
}
