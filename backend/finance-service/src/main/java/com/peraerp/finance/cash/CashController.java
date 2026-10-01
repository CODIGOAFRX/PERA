package com.peraerp.finance.cash;

import com.peraerp.finance.cash.CashDtos.CashMovementRequest;
import com.peraerp.finance.cash.CashDtos.CashRegisterRequest;
import com.peraerp.finance.cash.CashDtos.CashRegisterResponse;
import com.peraerp.finance.cash.CashDtos.CashSessionResponse;
import com.peraerp.finance.cash.CashDtos.CloseCashSessionRequest;
import com.peraerp.finance.cash.CashDtos.OpenCashSessionRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CashController {

    private final CashService service;

    public CashController(CashService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/cash-registers")
    List<CashRegisterResponse> registers() {
        return service.findRegisters();
    }

    @PostMapping("/api/v1/cash-registers")
    @ResponseStatus(HttpStatus.CREATED)
    CashRegisterResponse createRegister(@Valid @RequestBody CashRegisterRequest request) {
        return service.createRegister(request);
    }

    @PutMapping("/api/v1/cash-registers/{id}")
    CashRegisterResponse updateRegister(@PathVariable UUID id, @Valid @RequestBody CashRegisterRequest request) {
        return service.updateRegister(id, request);
    }

    @GetMapping("/api/v1/cash-sessions")
    PagedModel<CashSessionResponse> sessions(@RequestParam(required = false) UUID cashRegisterId,
                                             @RequestParam(required = false) CashSessionStatus status,
                                             Pageable pageable) {
        return new PagedModel<>(service.searchSessions(cashRegisterId, status, pageable));
    }

    @GetMapping("/api/v1/cash-sessions/{id}")
    CashSessionResponse session(@PathVariable UUID id) {
        return service.findSession(id);
    }

    @PostMapping("/api/v1/cash-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    CashSessionResponse open(@Valid @RequestBody OpenCashSessionRequest request) {
        return service.openSession(request);
    }

    @PostMapping("/api/v1/cash-sessions/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    CashSessionResponse addMovement(@PathVariable UUID id, @Valid @RequestBody CashMovementRequest request) {
        return service.addMovement(id, request);
    }

    @PostMapping("/api/v1/cash-sessions/{id}/close")
    CashSessionResponse close(@PathVariable UUID id, @Valid @RequestBody CloseCashSessionRequest request) {
        return service.closeSession(id, request);
    }
}
