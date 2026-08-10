package com.peraerp.masterdata.catalog;

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
@RequestMapping("/api/v1")
public class ProductClassificationController {
    private final ProductClassificationService service;

    public ProductClassificationController(ProductClassificationService service) {
        this.service = service;
    }

    @GetMapping("/product-natures")
    Page<ProductNatureResponse> searchNatures(@RequestParam(required = false) String query,
                                              @RequestParam(required = false) Boolean active,
                                              Pageable pageable) {
        return service.searchNatures(query, active, pageable);
    }

    @GetMapping("/product-natures/{id}")
    ProductNatureResponse findNature(@PathVariable UUID id) { return service.findNature(id); }

    @PostMapping("/product-natures")
    @ResponseStatus(HttpStatus.CREATED)
    ProductNatureResponse createNature(@Valid @RequestBody ProductNatureRequest request) {
        return service.createNature(request);
    }

    @PutMapping("/product-natures/{id}")
    ProductNatureResponse updateNature(@PathVariable UUID id,
                                        @Valid @RequestBody ProductNatureRequest request) {
        return service.updateNature(id, request);
    }

    @GetMapping("/product-supertypes")
    Page<ProductSupertypeResponse> searchSupertypes(@RequestParam(required = false) String query,
                                                    @RequestParam(required = false) UUID natureId,
                                                    @RequestParam(required = false) Boolean active,
                                                    Pageable pageable) {
        return service.searchSupertypes(query, natureId, active, pageable);
    }

    @GetMapping("/product-supertypes/{id}")
    ProductSupertypeResponse findSupertype(@PathVariable UUID id) { return service.findSupertype(id); }

    @PostMapping("/product-supertypes")
    @ResponseStatus(HttpStatus.CREATED)
    ProductSupertypeResponse createSupertype(@Valid @RequestBody ProductSupertypeRequest request) {
        return service.createSupertype(request);
    }

    @PutMapping("/product-supertypes/{id}")
    ProductSupertypeResponse updateSupertype(@PathVariable UUID id,
                                              @Valid @RequestBody ProductSupertypeRequest request) {
        return service.updateSupertype(id, request);
    }

    @GetMapping("/product-types")
    Page<ProductTypeResponse> searchTypes(@RequestParam(required = false) String query,
                                          @RequestParam(required = false) UUID supertypeId,
                                          @RequestParam(required = false) Boolean active,
                                          Pageable pageable) {
        return service.searchTypes(query, supertypeId, active, pageable);
    }

    @GetMapping("/product-types/{id}")
    ProductTypeResponse findType(@PathVariable UUID id) { return service.findType(id); }

    @PostMapping("/product-types")
    @ResponseStatus(HttpStatus.CREATED)
    ProductTypeResponse createType(@Valid @RequestBody ProductTypeRequest request) {
        return service.createType(request);
    }

    @PutMapping("/product-types/{id}")
    ProductTypeResponse updateType(@PathVariable UUID id, @Valid @RequestBody ProductTypeRequest request) {
        return service.updateType(id, request);
    }

    @GetMapping("/product-groups")
    Page<ProductGroupResponse> searchGroups(@RequestParam(required = false) String query,
                                            @RequestParam(required = false) UUID productTypeId,
                                            @RequestParam(required = false) Boolean active,
                                            Pageable pageable) {
        return service.searchGroups(query, productTypeId, active, pageable);
    }

    @GetMapping("/product-groups/{id}")
    ProductGroupResponse findGroup(@PathVariable UUID id) { return service.findGroup(id); }

    @PostMapping("/product-groups")
    @ResponseStatus(HttpStatus.CREATED)
    ProductGroupResponse createGroup(@Valid @RequestBody ProductGroupRequest request) {
        return service.createGroup(request);
    }

    @PutMapping("/product-groups/{id}")
    ProductGroupResponse updateGroup(@PathVariable UUID id, @Valid @RequestBody ProductGroupRequest request) {
        return service.updateGroup(id, request);
    }
}
