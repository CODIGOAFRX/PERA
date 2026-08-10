package com.peraerp.masterdata.catalog;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {
    private final PricingResolver resolver;

    public PricingController(PricingResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping({"/preview", "/resolve"})
    PricingResolveResponse resolve(@Valid @RequestBody PricingResolveRequest request) {
        return resolver.resolve(request);
    }
}
