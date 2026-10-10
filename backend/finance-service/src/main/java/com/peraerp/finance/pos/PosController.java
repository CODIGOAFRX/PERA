package com.peraerp.finance.pos;

import com.peraerp.finance.cash.CashDtos.CashMovementRequest;
import com.peraerp.finance.cash.CashDtos.CloseCashSessionRequest;
import com.peraerp.finance.pos.PosDtos.OpenPosSessionRequest;
import com.peraerp.finance.pos.PosDtos.PosRegisterResponse;
import com.peraerp.finance.pos.PosDtos.PosSessionSummary;
import com.peraerp.finance.pos.PosDtos.PosStatsResponse;
import com.peraerp.finance.pos.PosDtos.RefundRequest;
import com.peraerp.finance.pos.PosDtos.SaleRequest;
import com.peraerp.finance.pos.PosDtos.TicketResponse;
import com.peraerp.finance.pos.PosDtos.VoidTicketRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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
import java.util.List;
import java.util.UUID;

/** TPV: cajas, sesiones, tickets, devoluciones y cifras de venta. */
@RestController
@RequestMapping("/api/v1/pos")
public class PosController {

    private final PosService service;

    public PosController(PosService service) {
        this.service = service;
    }

    @GetMapping("/registers")
    List<PosRegisterResponse> registers() {
        return service.registers();
    }

    @PostMapping("/registers/{id}/open")
    @ResponseStatus(HttpStatus.CREATED)
    PosSessionSummary open(@PathVariable UUID id, @Valid @RequestBody OpenPosSessionRequest request) {
        return service.open(id, request);
    }

    @GetMapping("/sessions/{id}/summary")
    PosSessionSummary summary(@PathVariable UUID id) {
        return service.summary(id);
    }

    @PostMapping("/sessions/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    PosSessionSummary movement(@PathVariable UUID id, @Valid @RequestBody CashMovementRequest request) {
        return service.addMovement(id, request);
    }

    @PostMapping("/sessions/{id}/close")
    PosSessionSummary close(@PathVariable UUID id, @Valid @RequestBody CloseCashSessionRequest request) {
        return service.close(id, request);
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    TicketResponse sell(@Valid @RequestBody SaleRequest request) {
        return service.sell(request);
    }

    @GetMapping("/tickets")
    PagedModel<TicketResponse> tickets(@RequestParam(required = false) UUID sessionId,
                                       @RequestParam(required = false) UUID registerId,
                                       @RequestParam(required = false) PosTicketKind kind,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) String query,
                                       @RequestParam(required = false) String timeZone,
                                       Pageable pageable) {
        return new PagedModel<>(service.search(sessionId, registerId, kind, from, to, query, timeZone, pageable));
    }

    @GetMapping("/tickets/by-number/{number}")
    TicketResponse byNumber(@PathVariable String number) {
        return service.findByNumber(number);
    }

    @GetMapping("/tickets/{id}")
    TicketResponse ticket(@PathVariable UUID id) {
        return service.find(id);
    }

    @PostMapping("/tickets/{id}/void")
    TicketResponse voidTicket(@PathVariable UUID id, @Valid @RequestBody VoidTicketRequest request) {
        return service.voidTicket(id, request);
    }

    @PostMapping("/tickets/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    TicketResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return service.refund(id, request);
    }

    @GetMapping("/stats")
    PosStatsResponse stats(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) String timeZone) {
        return service.stats(from, to, timeZone);
    }
}
