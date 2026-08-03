package com.peraerp.masterdata.supplier;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
public class SupplierController {
    private final SupplierService service;
    public SupplierController(SupplierService service) { this.service = service; }
    @GetMapping Page<SupplierResponse> search(@RequestParam(required = false) String query, Pageable pageable) { return service.search(query, pageable); }
    @GetMapping("/{id}") SupplierResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) SupplierResponse create(@Valid @RequestBody SupplierRequest request) { return service.create(request); }
}
