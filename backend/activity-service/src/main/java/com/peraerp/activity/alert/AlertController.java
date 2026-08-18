package com.peraerp.activity.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    Page<AlertResponse> findAll(@RequestParam(required = false) AlertStatus status,
                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return service.findAll(status, page, size);
    }

    @GetMapping("/{id}")
    AlertResponse findById(@PathVariable UUID id) { return service.findById(id); }

    @PostMapping("/{id}/acknowledge")
    AlertResponse acknowledge(@PathVariable UUID id) { return service.acknowledge(id); }

    @PostMapping("/{id}/resolve")
    AlertResponse resolve(@PathVariable UUID id) { return service.resolve(id); }
}
