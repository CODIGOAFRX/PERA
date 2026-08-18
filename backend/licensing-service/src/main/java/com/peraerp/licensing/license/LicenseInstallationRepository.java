package com.peraerp.licensing.license;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LicenseInstallationRepository extends JpaRepository<LicenseInstallation, UUID> {
    boolean existsByTokenHash(byte[] tokenHash);

    long countByLicenseIdAndStatus(UUID licenseId, InstallationStatus status);

    Optional<LicenseInstallation> findByLicenseIdAndInstallationFingerprintHash(
            UUID licenseId, byte[] installationFingerprintHash);

    List<LicenseInstallation> findAllByLicenseIdAndCompanyIdOrderByActivatedAtAsc(UUID licenseId, UUID companyId);

    Optional<LicenseInstallation> findByTokenHash(byte[] tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from LicenseInstallation i where i.id = :id and i.licenseId = :licenseId " +
            "and i.companyId = :companyId")
    Optional<LicenseInstallation> findByIdForUpdate(@Param("id") UUID id, @Param("licenseId") UUID licenseId,
                                                    @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from LicenseInstallation i where i.licenseId = :licenseId " +
            "and i.installationFingerprintHash = :fingerprintHash")
    Optional<LicenseInstallation> findByFingerprintForUpdate(
            @Param("licenseId") UUID licenseId, @Param("fingerprintHash") byte[] fingerprintHash);
}
