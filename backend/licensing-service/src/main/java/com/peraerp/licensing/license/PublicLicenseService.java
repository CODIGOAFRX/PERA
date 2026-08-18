package com.peraerp.licensing.license;

import com.peraerp.licensing.security.SecretHashService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class PublicLicenseService {
    private static final int SECRET_GENERATION_ATTEMPTS = 5;
    private static final String INVALID = "INVALID";
    private static final String INSTALLATION_LIMIT_REACHED = "INSTALLATION_LIMIT_REACHED";

    private final LicenseRepository licenses;
    private final LicenseInstallationRepository installations;
    private final SecretHashService secrets;
    private final Clock clock;

    public PublicLicenseService(LicenseRepository licenses, LicenseInstallationRepository installations,
                                SecretHashService secrets, Clock clock) {
        this.licenses = licenses;
        this.installations = installations;
        this.secrets = secrets;
        this.clock = clock;
    }

    @Transactional
    public PublicLicenseResponse activate(ActivationRequest request) {
        String activationCode = request.activationCode().trim();
        byte[] activationHash = secrets.hashActivationCode(activationCode);
        Optional<License> candidate = licenses.findByActivationCodeHashForUpdate(activationHash);
        if (candidate.isEmpty() || !candidate.get().activationCodeHashMatches(activationHash)) {
            secrets.consumeDummyComparison(activationHash);
            return PublicLicenseResponse.invalid(INVALID);
        }

        License license = candidate.get();
        Instant now = clock.instant();
        LicenseStatus effectiveStatus = license.effectiveStatus(now);
        if (effectiveStatus == LicenseStatus.EXPIRED) {
            license.markExpired();
            return PublicLicenseResponse.invalid(license, LicenseStatus.EXPIRED.name());
        }
        if (!license.activationCanStartAt(now)) {
            return PublicLicenseResponse.invalid(license, effectiveStatus.name());
        }

        String installationId = request.installationId().trim();
        byte[] fingerprintHash = secrets.hashInstallationFingerprint(installationId);
        Optional<LicenseInstallation> currentInstallation =
                installations.findByFingerprintForUpdate(license.getId(), fingerprintHash);
        if (currentInstallation.isPresent()) {
            LicenseInstallation installation = currentInstallation.get();
            if (!installation.fingerprintHashMatches(fingerprintHash)
                    || !installation.getCompanyId().equals(license.getCompanyId())) {
                return PublicLicenseResponse.invalid(INVALID);
            }
            if (installation.getStatus() == InstallationStatus.REVOKED) {
                return PublicLicenseResponse.invalid(license, LicenseStatus.REVOKED.name());
            }
            GeneratedToken replacement = generateUniqueToken();
            installation.rotateToken(replacement.hash(), now);
            license.activate(now);
            return PublicLicenseResponse.valid(license, now, replacement.secret());
        }

        long activeInstallations = installations.countByLicenseIdAndStatus(
                license.getId(), InstallationStatus.ACTIVE);
        if (activeInstallations >= license.getMaxInstallations()) {
            return PublicLicenseResponse.invalid(license, INSTALLATION_LIMIT_REACHED);
        }

        GeneratedToken token = generateUniqueToken();
        installations.save(new LicenseInstallation(license.getCompanyId(), license.getId(), fingerprintHash,
                token.hash(), now));
        license.activate(now);
        return PublicLicenseResponse.valid(license, now, token.secret());
    }

    @Transactional
    public PublicLicenseResponse validate(InstallationTokenRequest request) {
        InstallationLookup lookup = findInstallation(request.installationToken());
        if (lookup.installation() == null) {
            return PublicLicenseResponse.invalid(INVALID);
        }
        return validateInstallation(lookup, null);
    }

    @Transactional
    public PublicLicenseResponse rotate(InstallationTokenRequest request) {
        InstallationLookup lookup = findInstallation(request.installationToken());
        if (lookup.installation() == null) {
            return PublicLicenseResponse.invalid(INVALID);
        }
        GeneratedToken replacement = generateUniqueToken();
        return validateInstallation(lookup, replacement);
    }

    private InstallationLookup findInstallation(String presentedToken) {
        String token = presentedToken.trim();
        byte[] tokenHash = secrets.hashInstallationToken(token);
        Optional<LicenseInstallation> candidate = installations.findByTokenHash(tokenHash);
        if (candidate.isEmpty() || !candidate.get().tokenHashMatches(tokenHash)) {
            secrets.consumeDummyComparison(tokenHash);
            return new InstallationLookup(null, tokenHash);
        }
        return new InstallationLookup(candidate.get(), tokenHash);
    }

    private PublicLicenseResponse validateInstallation(InstallationLookup lookup, GeneratedToken replacement) {
        LicenseInstallation unresolvedInstallation = lookup.installation();
        Optional<License> candidate = licenses.findByIdForUpdate(unresolvedInstallation.getLicenseId());
        if (candidate.isEmpty() || !candidate.get().getCompanyId().equals(unresolvedInstallation.getCompanyId())) {
            return PublicLicenseResponse.invalid(INVALID);
        }

        License license = candidate.get();
        Optional<LicenseInstallation> lockedInstallation = installations.findByIdForUpdate(
                unresolvedInstallation.getId(), license.getId(), license.getCompanyId());
        if (lockedInstallation.isEmpty() || !lockedInstallation.get().tokenHashMatches(lookup.tokenHash())) {
            secrets.consumeDummyComparison(lookup.tokenHash());
            return PublicLicenseResponse.invalid(INVALID);
        }
        LicenseInstallation installation = lockedInstallation.get();
        if (installation.getStatus() == InstallationStatus.REVOKED) {
            return PublicLicenseResponse.invalid(license, LicenseStatus.REVOKED.name());
        }

        Instant now = clock.instant();
        LicenseStatus status = license.effectiveStatus(now);
        if (status == LicenseStatus.EXPIRED) {
            license.markExpired();
            return PublicLicenseResponse.invalid(license, LicenseStatus.EXPIRED.name());
        }
        if (status != LicenseStatus.ACTIVE) {
            return PublicLicenseResponse.invalid(license, status.name());
        }

        if (replacement == null) {
            installation.validateAt(now);
            return PublicLicenseResponse.valid(license, now, null);
        }
        installation.rotateToken(replacement.hash(), now);
        return PublicLicenseResponse.valid(license, now, replacement.secret());
    }

    private GeneratedToken generateUniqueToken() {
        for (int attempt = 0; attempt < SECRET_GENERATION_ATTEMPTS; attempt++) {
            String token = secrets.generateInstallationToken();
            byte[] hash = secrets.hashInstallationToken(token);
            if (!installations.existsByTokenHash(hash)) {
                return new GeneratedToken(token, hash);
            }
        }
        throw new IllegalStateException("No se pudo generar un token de instalación único.");
    }

    private record GeneratedToken(String secret, byte[] hash) {
        private GeneratedToken {
            hash = hash.clone();
        }

        @Override
        public byte[] hash() {
            return hash.clone();
        }
    }

    private record InstallationLookup(LicenseInstallation installation, byte[] tokenHash) {
        private InstallationLookup {
            tokenHash = tokenHash.clone();
        }

        @Override
        public byte[] tokenHash() {
            return tokenHash.clone();
        }
    }
}
