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
@RequestMapping("/api/v1/packaging-types")
public class PackagingTypeController {
    private final PackagingTypeService service;

    public PackagingTypeController(PackagingTypeService service) {
        this.service = service;
    }

    @GetMapping
    Page<PackagingTypeResponse> search(@RequestParam(required = false) String query,
                                       @RequestParam(required = false) Boolean returnable,
                                       @RequestParam(required = false) Boolean active,
                                       Pageable pageable) {
        return service.search(query, returnable, active, pageable);
    }

    @GetMapping("/{id}")
    PackagingTypeResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PackagingTypeResponse create(@Valid @RequestBody PackagingTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    PackagingTypeResponse update(@PathVariable UUID id, @Valid @RequestBody PackagingTypeRequest request) {
        return service.update(id, request);
    }
}
