package com.peraerp.finance.currency;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/currency-conversions")
public class CurrencyConversionController {
    private final ExchangeRateService service;
    public CurrencyConversionController(ExchangeRateService service) { this.service = service; }
    @PostMapping CurrencyConversionResponse convert(@Valid @RequestBody CurrencyConversionRequest request) {
        return service.convert(request);
    }
}
