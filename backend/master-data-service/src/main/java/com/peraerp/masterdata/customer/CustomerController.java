package com.peraerp.masterdata.customer;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service = service; }

    @GetMapping
    Page<CustomerResponse> search(@RequestParam(required = false) String query, Pageable pageable) {
        return service.search(query, pageable);
    }
    @GetMapping("/{id}")
    CustomerResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CustomerResponse create(@Valid @RequestBody CustomerRequest request) { return service.create(request); }
    @PutMapping("/{id}")
    CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        return service.update(id, request);
    }
}
