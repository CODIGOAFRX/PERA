package com.peraerp.masterdata.catalog;

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
@RequestMapping("/api/v1/tariffs")
public class TariffController {
    private final TariffService service;

    public TariffController(TariffService service) {
        this.service = service;
    }

    @GetMapping
    Page<TariffResponse> search(@RequestParam(required = false) String query,
                                @RequestParam(required = false) UUID customerId,
                                @RequestParam(required = false) UUID natureId,
                                @RequestParam(required = false) UUID supertypeId,
                                @RequestParam(required = false) UUID typeId,
                                @RequestParam(required = false) PricingScope scope,
                                @RequestParam(required = false) Boolean active,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validOn,
                                Pageable pageable) {
        return service.searchTariffs(query, customerId, natureId, supertypeId, typeId, scope, active, validOn,
                pageable);
    }

    @GetMapping("/{id}")
    TariffResponse findById(@PathVariable UUID id) {
        return service.findTariff(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TariffResponse create(@Valid @RequestBody TariffRequest request) {
        return service.createTariff(request);
    }

    @PutMapping("/{id}")
    TariffResponse update(@PathVariable UUID id, @Valid @RequestBody TariffRequest request) {
        return service.updateTariff(id, request);
    }

    @GetMapping("/{tariffId}/items")
    List<TariffItemResponse> listItems(@PathVariable UUID tariffId) {
        return service.listItems(tariffId);
    }

    @PostMapping("/{tariffId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    TariffItemResponse createItem(@PathVariable UUID tariffId,
                                  @Valid @RequestBody TariffItemRequest request) {
        return service.createItem(tariffId, request);
    }

    @PutMapping("/{tariffId}/items/{itemId}")
    TariffItemResponse updateItem(@PathVariable UUID tariffId, @PathVariable UUID itemId,
                                  @Valid @RequestBody TariffItemRequest request) {
        return service.updateItem(tariffId, itemId, request);
    }

    @GetMapping("/{tariffId}/rules")
    List<PricingRuleResponse> listRules(@PathVariable UUID tariffId) {
        return service.listRules(tariffId);
    }

    @PostMapping("/{tariffId}/rules")
    @ResponseStatus(HttpStatus.CREATED)
    PricingRuleResponse createRule(@PathVariable UUID tariffId,
                                   @Valid @RequestBody PricingRuleRequest request) {
        return service.createRule(tariffId, request);
    }

    @PutMapping("/{tariffId}/rules/{ruleId}")
    PricingRuleResponse updateRule(@PathVariable UUID tariffId, @PathVariable UUID ruleId,
                                   @Valid @RequestBody PricingRuleRequest request) {
        return service.updateRule(tariffId, ruleId, request);
    }
}
