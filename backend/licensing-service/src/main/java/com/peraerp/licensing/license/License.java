package com.peraerp.licensing.license;

import com.peraerp.platform.domain.BusinessRuleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

@Entity
@Table(name = "licenses", uniqueConstraints =
        @UniqueConstraint(name = "uk_license_activation_hash", columnNames = "activation_code_hash"))
public class License extends AuditedEntity {
    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "activation_code_hash", nullable = false, updatable = false)
    private byte[] activationCodeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenseStatus status = LicenseStatus.DRAFT;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "grace_period_seconds", nullable = false)
    private long gracePeriodSeconds;

    @Column(name = "max_installations", nullable = false)
    private int maxInstallations;

    @Column(name = "check_interval_seconds", nullable = false)
    private long checkIntervalSeconds;

    @Column(name = "first_activated_at")
    private Instant firstActivatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "license_features", joinColumns = @JoinColumn(name = "license_id"))
    @Column(name = "feature_code", nullable = false, length = 64)
    private Set<String> features = new TreeSet<>();

    protected License() {
    }

    public License(UUID companyId, String displayName, byte[] activationCodeHash, Instant validFrom,
                   Instant validUntil, long gracePeriodSeconds, int maxInstallations,
                   long checkIntervalSeconds, Set<String> features) {
        this.companyId = companyId;
        this.displayName = displayName;
        this.activationCodeHash = activationCodeHash.clone();
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.gracePeriodSeconds = gracePeriodSeconds;
        this.maxInstallations = maxInstallations;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.features.addAll(features);
    }

    public LicenseStatus effectiveStatus(Instant now) {
        if (status == LicenseStatus.REVOKED || status == LicenseStatus.EXPIRED) {
            return status;
        }
        if (now.isAfter(graceUntil())) {
            return LicenseStatus.EXPIRED;
        }
        return status;
    }

    public boolean activationCodeHashMatches(byte[] candidateHash) {
        return MessageDigest.isEqual(activationCodeHash, candidateHash);
    }

    public boolean activationCanStartAt(Instant now) {
        LicenseStatus effective = effectiveStatus(now);
        return effective == LicenseStatus.DRAFT && !now.isBefore(validFrom)
                || effective == LicenseStatus.ACTIVE;
    }

    public void activate(Instant now) {
        if (status == LicenseStatus.DRAFT) {
            status = LicenseStatus.ACTIVE;
            if (firstActivatedAt == null) {
                firstActivatedAt = now;
            }
        }
    }

    public void suspend(Instant now) {
        if (effectiveStatus(now) == LicenseStatus.EXPIRED) {
            status = LicenseStatus.EXPIRED;
            throw new BusinessRuleException("La licencia ya ha caducado.");
        }
        if (status == LicenseStatus.REVOKED) {
            throw new BusinessRuleException("Una licencia revocada no se puede suspender.");
        }
        status = LicenseStatus.SUSPENDED;
    }

    public void resume(Instant now) {
        if (effectiveStatus(now) == LicenseStatus.EXPIRED) {
            status = LicenseStatus.EXPIRED;
            throw new BusinessRuleException("La licencia ya ha caducado.");
        }
        if (status != LicenseStatus.SUSPENDED) {
            throw new BusinessRuleException("Solo se puede reanudar una licencia suspendida.");
        }
        status = firstActivatedAt == null ? LicenseStatus.DRAFT : LicenseStatus.ACTIVE;
    }

    public void revoke() {
        status = LicenseStatus.REVOKED;
    }

    public void markExpired() {
        if (status != LicenseStatus.REVOKED) {
            status = LicenseStatus.EXPIRED;
        }
    }

    public Instant nextCheckAt(Instant now) {
        Instant requested;
        try {
            requested = now.plusSeconds(checkIntervalSeconds);
        } catch (DateTimeException exception) {
            requested = graceUntil();
        }
        Instant graceUntil = graceUntil();
        return requested.isAfter(graceUntil) ? graceUntil : requested;
    }

    public Instant graceUntil() {
        try {
            return validUntil.plusSeconds(gracePeriodSeconds);
        } catch (DateTimeException exception) {
            return Instant.MAX;
        }
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LicenseStatus getStatus() {
        return status;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public long getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public int getMaxInstallations() {
        return maxInstallations;
    }

    public long getCheckIntervalSeconds() {
        return checkIntervalSeconds;
    }

    public Instant getFirstActivatedAt() {
        return firstActivatedAt;
    }

    public Set<String> getFeatures() {
        return Collections.unmodifiableSet(new TreeSet<>(features));
    }
}
