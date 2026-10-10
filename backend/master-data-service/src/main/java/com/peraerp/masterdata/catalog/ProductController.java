package com.peraerp.masterdata.catalog;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService service;
    public ProductController(ProductService service) { this.service = service; }
    @GetMapping Page<ProductResponse> search(@RequestParam(required = false) String query, Pageable pageable) { return service.search(query, pageable); }
    /** Lo que lee un escáner: primero se busca como código de barras y después como código del artículo. */
    @GetMapping("/lookup") ProductResponse lookup(@RequestParam String code) { return service.lookup(code); }
    @GetMapping("/{id}") ProductResponse findById(@PathVariable UUID id) { return service.findById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) ProductResponse create(@Valid @RequestBody ProductRequest request) { return service.create(request); }
    @PutMapping("/{id}") ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) { return service.update(id, request); }
}
