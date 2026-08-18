package com.peraerp.licensing.license;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/v1/licenses")
public class PublicLicenseController {
    private final PublicLicenseService service;

    public PublicLicenseController(PublicLicenseService service) {
        this.service = service;
    }

    @PostMapping("/activate")
    ResponseEntity<PublicLicenseResponse> activate(@Valid @RequestBody ActivationRequest request) {
        return noStore(service.activate(request));
    }

    @PostMapping("/validate")
    ResponseEntity<PublicLicenseResponse> validate(@Valid @RequestBody InstallationTokenRequest request) {
        return noStore(service.validate(request));
    }

    @PostMapping("/tokens/rotate")
    ResponseEntity<PublicLicenseResponse> rotate(@Valid @RequestBody InstallationTokenRequest request) {
        return noStore(service.rotate(request));
    }

    private ResponseEntity<PublicLicenseResponse> noStore(PublicLicenseResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
