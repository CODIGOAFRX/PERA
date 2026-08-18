package com.peraerp.licensing.license;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/licenses")
public class AdminLicenseController {
    private final AdminLicenseService service;

    public AdminLicenseController(AdminLicenseService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<LicenseCreatedResponse> create(@Valid @RequestBody CreateLicenseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.create(request));
    }

    @GetMapping
    LicensePageResponse list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    LicenseDetailResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping("/{id}/suspend")
    LicenseDetailResponse suspend(@PathVariable UUID id) {
        return service.suspend(id);
    }

    @PostMapping("/{id}/resume")
    LicenseDetailResponse resume(@PathVariable UUID id) {
        return service.resume(id);
    }

    @PostMapping("/{id}/revoke")
    LicenseDetailResponse revoke(@PathVariable UUID id) {
        return service.revoke(id);
    }

    @PostMapping("/{licenseId}/installations/{installationId}/revoke")
    LicenseDetailResponse revokeInstallation(@PathVariable UUID licenseId, @PathVariable UUID installationId) {
        return service.revokeInstallation(licenseId, installationId);
    }
}
