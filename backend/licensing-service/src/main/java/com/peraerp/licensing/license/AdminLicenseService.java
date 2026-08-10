package com.peraerp.licensing.license;

import com.peraerp.licensing.config.CurrentCompanyProvider;
import com.peraerp.licensing.security.SecretHashService;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class AdminLicenseService {
    private static final int SECRET_GENERATION_ATTEMPTS = 5;

    private final LicenseRepository licenses;
    private final LicenseInstallationRepository installations;
    private final CurrentCompanyProvider companyProvider;
    private final SecretHashService secrets;
    private final Clock clock;

    public AdminLicenseService(LicenseRepository licenses, LicenseInstallationRepository installations,
                               CurrentCompanyProvider companyProvider, SecretHashService secrets, Clock clock) {
        this.licenses = licenses;
        this.installations = installations;
        this.companyProvider = companyProvider;
        this.secrets = secrets;
        this.clock = clock;
    }

    @Transactional
    public LicenseCreatedResponse create(CreateLicenseRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        Instant now = clock.instant();
        Instant validFrom = request.validFrom() == null ? now : request.validFrom();
        if (!request.validUntil().isAfter(validFrom) || !request.validUntil().isAfter(now)) {
            throw new BusinessRuleException("La fecha final debe ser posterior al inicio y al momento actual.");
        }

        String activationCode = generateUniqueActivationCode();
        License license = licenses.save(new License(companyId, request.displayName().trim(),
                secrets.hashActivationCode(activationCode), validFrom, request.validUntil(),
                request.gracePeriodSeconds(), request.maxInstallations(), request.checkIntervalSeconds(),
                normalizeFeatures(request.features())));
        return new LicenseCreatedResponse(toSummary(license, now), activationCode);
    }

    @Transactional(readOnly = true)
    public LicensePageResponse list(Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        Instant now = clock.instant();
        return LicensePageResponse.from(licenses.findAllByCompanyIdOrderByCreatedAtDesc(companyId, pageable)
                .map(license -> toSummary(license, now)));
    }

    @Transactional(readOnly = true)
    public LicenseDetailResponse findById(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        License license = licenses.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Licencia", id));
        return toDetail(license, clock.instant());
    }

    @Transactional
    public LicenseDetailResponse suspend(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        License license = requireForUpdate(id, companyId);
        license.suspend(clock.instant());
        return toDetail(license, clock.instant());
    }

    @Transactional
    public LicenseDetailResponse resume(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        License license = requireForUpdate(id, companyId);
        license.resume(clock.instant());
        return toDetail(license, clock.instant());
    }

    @Transactional
    public LicenseDetailResponse revoke(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        Instant now = clock.instant();
        License license = requireForUpdate(id, companyId);
        license.revoke();
        installations.findAllByLicenseIdAndCompanyIdOrderByActivatedAtAsc(id, companyId)
                .forEach(installation -> installation.revoke(now));
        return toDetail(license, now);
    }

    @Transactional
    public LicenseDetailResponse revokeInstallation(UUID licenseId, UUID installationId) {
        UUID companyId = companyProvider.requireCompanyId();
        License license = requireForUpdate(licenseId, companyId);
        LicenseInstallation installation = installations
                .findByIdForUpdate(installationId, licenseId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Instalación", installationId));
        installation.revoke(clock.instant());
        return toDetail(license, clock.instant());
    }

    private License requireForUpdate(UUID id, UUID companyId) {
        return licenses.findByIdAndCompanyIdForUpdate(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Licencia", id));
    }

    private String generateUniqueActivationCode() {
        for (int attempt = 0; attempt < SECRET_GENERATION_ATTEMPTS; attempt++) {
            String candidate = secrets.generateActivationCode();
            if (!licenses.existsByActivationCodeHash(secrets.hashActivationCode(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de activación único.");
    }

    private Set<String> normalizeFeatures(Set<String> features) {
        Set<String> normalized = new TreeSet<>();
        if (features != null) {
            features.stream().map(String::trim).map(value -> value.toLowerCase(Locale.ROOT))
                    .forEach(normalized::add);
        }
        return normalized;
    }

    private LicenseSummaryResponse toSummary(License license, Instant now) {
        int activeInstallations = Math.toIntExact(installations.countByLicenseIdAndStatus(
                license.getId(), InstallationStatus.ACTIVE));
        return new LicenseSummaryResponse(license.getId(), license.getCompanyId(), license.getDisplayName(),
                license.effectiveStatus(now), license.getValidFrom(), license.getValidUntil(), license.graceUntil(),
                license.getGracePeriodSeconds(), license.getMaxInstallations(), activeInstallations,
                license.getCheckIntervalSeconds(), license.getFeatures(), license.getFirstActivatedAt(),
                license.getCreatedAt(), license.getUpdatedAt());
    }

    private LicenseDetailResponse toDetail(License license, Instant now) {
        return new LicenseDetailResponse(toSummary(license, now), installations
                .findAllByLicenseIdAndCompanyIdOrderByActivatedAtAsc(license.getId(), license.getCompanyId())
                .stream().map(InstallationResponse::from).toList());
    }
}
