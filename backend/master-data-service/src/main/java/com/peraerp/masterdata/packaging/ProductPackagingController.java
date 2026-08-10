package com.peraerp.masterdata.packaging;

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
@RequestMapping("/api/v1/product-packaging")
public class ProductPackagingController {
    private final ProductPackagingService service;

    public ProductPackagingController(ProductPackagingService service) {
        this.service = service;
    }

    @GetMapping
    Page<ProductPackagingResponse> search(@RequestParam(required = false) String query,
                                          @RequestParam(required = false) UUID productId,
                                          @RequestParam(required = false) UUID packagingTypeId,
                                          @RequestParam(required = false) Boolean defaultPackaging,
                                          @RequestParam(required = false) Boolean active,
                                          Pageable pageable) {
        return service.search(query, productId, packagingTypeId, defaultPackaging, active, pageable);
    }

    @GetMapping("/{id}")
    ProductPackagingResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProductPackagingResponse create(@Valid @RequestBody ProductPackagingRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    ProductPackagingResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody ProductPackagingRequest request) {
        return service.update(id, request);
    }
}
