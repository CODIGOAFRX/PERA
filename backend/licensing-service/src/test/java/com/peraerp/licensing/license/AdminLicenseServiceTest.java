package com.peraerp.licensing.license;

import com.peraerp.licensing.config.CurrentCompanyProvider;
import com.peraerp.licensing.security.SecretHashService;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLicenseServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    @Mock LicenseRepository licenses;
    @Mock LicenseInstallationRepository installations;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private final SecretHashService secrets = new SecretHashService(
            "test-pepper-that-is-longer-than-thirty-two-bytes", new SecureRandom());
    private AdminLicenseService service;

    @BeforeEach
    void setUp() {
        service = new AdminLicenseService(licenses, installations, companyProvider, secrets,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCompanyScopedDraftAndReturnsActivationSecretOnlyOnce() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(licenses.existsByActivationCodeHash(any(byte[].class))).thenReturn(false);
        when(licenses.save(any(License.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(installations.countByLicenseIdAndStatus(any(), any())).thenReturn(0L);

        LicenseCreatedResponse response = service.create(new CreateLicenseRequest(" Equipo comercial ", null,
                NOW.plusSeconds(86_400), 3_600, 3, 300, Set.of("SALES", "reports.basic")));

        assertThat(response.activationCode()).startsWith("PERA-");
        assertThat(response.license().companyId()).isEqualTo(companyId);
        assertThat(response.license().status()).isEqualTo(LicenseStatus.DRAFT);
        assertThat(response.license().features()).containsExactly("reports.basic", "sales");

        var captor = org.mockito.ArgumentCaptor.forClass(License.class);
        verify(licenses).save(captor.capture());
        License persisted = captor.getValue();
        assertThat(persisted.activationCodeHashMatches(secrets.hashActivationCode(response.activationCode())))
                .isTrue();
        assertThat(persisted.activationCodeHashMatches(secrets.hashActivationCode("not-the-secret"))).isFalse();
    }

    @Test
    void detailLookupCannotCrossTenantBoundary() {
        UUID licenseId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(licenses.findByIdAndCompanyId(licenseId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(licenseId)).isInstanceOf(ResourceNotFoundException.class);

        verify(licenses).findByIdAndCompanyId(licenseId, companyId);
        verify(licenses, never()).findById(licenseId);
    }

    @Test
    void suspendAndResumePreserveActivatedLifecycle() {
        License license = license();
        license.activate(NOW.minusSeconds(60));
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(licenses.findByIdAndCompanyIdForUpdate(license.getId(), companyId))
                .thenReturn(Optional.of(license));
        when(installations.countByLicenseIdAndStatus(license.getId(), InstallationStatus.ACTIVE)).thenReturn(0L);
        when(installations.findAllByLicenseIdAndCompanyIdOrderByActivatedAtAsc(license.getId(), companyId))
                .thenReturn(List.of());

        assertThat(service.suspend(license.getId()).license().status()).isEqualTo(LicenseStatus.SUSPENDED);
        assertThat(service.resume(license.getId()).license().status()).isEqualTo(LicenseStatus.ACTIVE);
    }

    @Test
    void mutationsCannotCrossTenantBoundary() {
        UUID licenseId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(licenses.findByIdAndCompanyIdForUpdate(licenseId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(licenseId)).isInstanceOf(ResourceNotFoundException.class);

        verify(installations, never()).findAllByLicenseIdAndCompanyIdOrderByActivatedAtAsc(any(), any());
    }

    @Test
    void revokingLicenseAlsoRevokesEveryInstallation() {
        License license = license();
        license.activate(NOW.minusSeconds(60));
        LicenseInstallation installation = new LicenseInstallation(companyId, license.getId(),
                secrets.hashInstallationFingerprint("device-admin-revoke"),
                secrets.hashInstallationToken("perat_admin-revoke-token-abcdefghijklmnopqrstuvwxyz"),
                NOW.minusSeconds(60));
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(licenses.findByIdAndCompanyIdForUpdate(license.getId(), companyId))
                .thenReturn(Optional.of(license));
        when(installations.findAllByLicenseIdAndCompanyIdOrderByActivatedAtAsc(license.getId(), companyId))
                .thenReturn(List.of(installation));
        when(installations.countByLicenseIdAndStatus(license.getId(), InstallationStatus.ACTIVE)).thenReturn(0L);

        LicenseDetailResponse response = service.revoke(license.getId());

        assertThat(response.license().status()).isEqualTo(LicenseStatus.REVOKED);
        assertThat(response.installations()).singleElement()
                .extracting(InstallationResponse::status).isEqualTo(InstallationStatus.REVOKED);
    }

    private License license() {
        return new License(companyId, "Licencia", secrets.hashActivationCode("PERA-test"),
                NOW.minusSeconds(3_600), NOW.plusSeconds(86_400), 3_600, 2, 300, Set.of("sales"));
    }
}
