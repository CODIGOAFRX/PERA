package com.peraerp.sales.commission;

import com.peraerp.sales.commission.CommissionDtos.CalculateRequest;
import com.peraerp.sales.commission.CommissionDtos.CalculateResponse;
import com.peraerp.sales.commission.CommissionDtos.CommissionResponse;
import com.peraerp.sales.commission.CommissionDtos.RuleRequest;
import com.peraerp.sales.commission.CommissionDtos.RuleResponse;
import com.peraerp.sales.commission.CommissionDtos.SettleRequest;
import com.peraerp.sales.commission.CommissionDtos.Totals;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CommissionController {
    private final CommissionService service;

    public CommissionController(CommissionService service) { this.service = service; }

    @GetMapping("/commission-rules")
    List<RuleResponse> rules(@RequestParam UUID salespersonId) { return service.rules(salespersonId); }

    @PostMapping("/commission-rules")
    @ResponseStatus(HttpStatus.CREATED)
    RuleResponse createRule(@Valid @RequestBody RuleRequest request) { return service.createRule(request); }

    @PutMapping("/commission-rules/{id}")
    RuleResponse updateRule(@PathVariable UUID id, @Valid @RequestBody RuleRequest request) {
        return service.updateRule(id, request);
    }

    @PostMapping("/commissions/calculate")
    CalculateResponse calculate(@Valid @RequestBody CalculateRequest request) { return service.calculate(request); }

    @GetMapping("/commissions")
    Page<CommissionResponse> search(@RequestParam(required = false) UUID salespersonId,
                                    @RequestParam(required = false) SalesCommission.Status status,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                    @RequestParam(required = false) Boolean collected, Pageable pageable) {
        return service.search(salespersonId, status, fromDate, toDate, collected, pageable);
    }

    @GetMapping("/commissions/totals")
    Totals totals(@RequestParam(required = false) UUID salespersonId,
                  @RequestParam(required = false) SalesCommission.Status status,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                  @RequestParam(required = false) Boolean collected) {
        return service.totals(salespersonId, status, fromDate, toDate, collected);
    }

    @GetMapping("/commissions/{id}")
    CommissionResponse findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping("/commissions/settle")
    List<CommissionResponse> settle(@Valid @RequestBody SettleRequest request) { return service.settle(request); }

    @PostMapping("/commissions/{id}/reopen")
    CommissionResponse reopen(@PathVariable UUID id) { return service.reopen(id); }
}
