package com.peraerp.sales.dashboard;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/sales-dashboard")
public class SalesDashboardController {

    private final SalesDashboardService service;

    public SalesDashboardController(SalesDashboardService service) {
        this.service = service;
    }

    @GetMapping
    SalesDashboardResponse summarize(@RequestParam(defaultValue = "6") @Min(3) @Max(12) int months) {
        return service.summarize(months);
    }
}
