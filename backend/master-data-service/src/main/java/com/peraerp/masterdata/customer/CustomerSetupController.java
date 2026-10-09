package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.customer.CustomerFileDtos.CatalogItemRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.CatalogItemResponse;
import com.peraerp.masterdata.customer.CustomerFileDtos.SalespersonRequest;
import com.peraerp.masterdata.customer.CustomerFileDtos.SalespersonResponse;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CustomerSetupController {
    private final CustomerSetupService service;

    public CustomerSetupController(CustomerSetupService service) { this.service = service; }

    @GetMapping("/customer-catalog")
    List<CatalogItemResponse> catalog(@RequestParam(required = false) CustomerCatalogKind kind,
                                      @RequestParam(required = false) Boolean active) {
        return service.catalog(kind, active);
    }

    @PostMapping("/customer-catalog")
    @ResponseStatus(HttpStatus.CREATED)
    CatalogItemResponse createCatalogItem(@Valid @RequestBody CatalogItemRequest request) {
        return service.createCatalogItem(request);
    }

    @PutMapping("/customer-catalog/{id}")
    CatalogItemResponse updateCatalogItem(@PathVariable UUID id, @Valid @RequestBody CatalogItemRequest request) {
        return service.updateCatalogItem(id, request);
    }

    @GetMapping("/salespeople")
    List<SalespersonResponse> salespeople(@RequestParam(required = false) Boolean active) {
        return service.salespeople(active);
    }

    @GetMapping("/salespeople/{id}")
    SalespersonResponse findSalesperson(@PathVariable UUID id) {
        return service.findSalesperson(id);
    }

    @PostMapping("/salespeople")
    @ResponseStatus(HttpStatus.CREATED)
    SalespersonResponse createSalesperson(@Valid @RequestBody SalespersonRequest request) {
        return service.createSalesperson(request);
    }

    @PutMapping("/salespeople/{id}")
    SalespersonResponse updateSalesperson(@PathVariable UUID id, @Valid @RequestBody SalespersonRequest request) {
        return service.updateSalesperson(id, request);
    }
}
