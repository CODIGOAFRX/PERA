package com.peraerp.licensing.license;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "license_installations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_license_installation_fingerprint",
                columnNames = {"license_id", "installation_fingerprint_hash"}),
        @UniqueConstraint(name = "uk_license_installation_token", columnNames = "token_hash")
})
public class LicenseInstallation extends AuditedEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "license_id", nullable = false, updatable = false)
    private UUID licenseId;

    @Column(name = "installation_fingerprint_hash", nullable = false, updatable = false)
    private byte[] installationFingerprintHash;

    @Column(name = "token_hash", nullable = false)
    private byte[] tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallationStatus status = InstallationStatus.ACTIVE;

    @Column(name = "token_issued_at", nullable = false)
    private Instant tokenIssuedAt;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected LicenseInstallation() {
    }

    public LicenseInstallation(UUID companyId, UUID licenseId, byte[] installationFingerprintHash,
                               byte[] tokenHash, Instant activatedAt) {
        this.companyId = companyId;
        this.licenseId = licenseId;
        this.installationFingerprintHash = installationFingerprintHash.clone();
        this.tokenHash = tokenHash.clone();
        this.tokenIssuedAt = activatedAt;
        this.activatedAt = activatedAt;
    }

    public boolean fingerprintHashMatches(byte[] candidateHash) {
        return MessageDigest.isEqual(installationFingerprintHash, candidateHash);
    }

    public boolean tokenHashMatches(byte[] candidateHash) {
        return MessageDigest.isEqual(tokenHash, candidateHash);
    }

    public void validateAt(Instant now) {
        lastValidatedAt = now;
    }

    public void rotateToken(byte[] replacementHash, Instant now) {
        tokenHash = replacementHash.clone();
        tokenIssuedAt = now;
        lastValidatedAt = now;
    }

    public void revoke(Instant now) {
        if (status != InstallationStatus.REVOKED) {
            status = InstallationStatus.REVOKED;
            revokedAt = now;
        }
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getLicenseId() {
        return licenseId;
    }

    public InstallationStatus getStatus() {
        return status;
    }

    public Instant getTokenIssuedAt() {
        return tokenIssuedAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getLastValidatedAt() {
        return lastValidatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
