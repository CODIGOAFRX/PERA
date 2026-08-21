package com.peraerp.sales.verifactu.api;

import com.peraerp.sales.verifactu.VerifactuSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/verifactu-settings")
public class VerifactuSettingsController {

    private final VerifactuSettingsService service;

    public VerifactuSettingsController(VerifactuSettingsService service) {
        this.service = service;
    }

    @GetMapping("/current")
    VerifactuSettingsResponse current() {
        return service.current();
    }

    @PutMapping("/current")
    VerifactuSettingsResponse update(@Valid @RequestBody VerifactuSettingsRequest request) {
        return service.update(request);
    }
}
