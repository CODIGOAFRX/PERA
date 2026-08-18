package com.peraerp.licensing.license;

import com.peraerp.licensing.security.SecretHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicLicenseServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");
    private static final String ACTIVATION_CODE = "PERA-abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Mock LicenseRepository licenses;
    @Mock LicenseInstallationRepository installations;

    private final SecretHashService secrets = new SecretHashService(
            "test-pepper-that-is-longer-than-thirty-two-bytes", new SecureRandom());
    private PublicLicenseService service;

    @BeforeEach
    void setUp() {
        service = serviceAt(NOW);
    }

    @Test
    void activatesInstallationWithSeparateOpaqueTokenAndStoredHashesOnly() {
        License license = license(NOW.plusSeconds(86_400), 3_600, 2);
        stubActivation(license, 0);
        when(installations.existsByTokenHash(any(byte[].class))).thenReturn(false);
        when(installations.save(any(LicenseInstallation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicLicenseResponse response = service.activate(new ActivationRequest(ACTIVATION_CODE, "device-00000001"));

        assertThat(response.valid()).isTrue();
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.installationToken()).startsWith("perat_").isNotEqualTo(ACTIVATION_CODE);
        assertThat(response.nextCheckAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(response.companyId()).isEqualTo(license.getCompanyId());
        assertThat(license.getStatus()).isEqualTo(LicenseStatus.ACTIVE);

        var captor = org.mockito.ArgumentCaptor.forClass(LicenseInstallation.class);
        verify(installations).save(captor.capture());
        assertThat(captor.getValue().tokenHashMatches(
                secrets.hashInstallationToken(response.installationToken()))).isTrue();
        assertThat(captor.getValue().fingerprintHashMatches(
                secrets.hashInstallationFingerprint("device-00000001"))).isTrue();
    }

    @Test
    void rejectsActivationAtInstallationLimitWithoutChangingDraftState() {
        License license = license(NOW.plusSeconds(86_400), 0, 2);
        stubActivation(license, 2);

        PublicLicenseResponse response = service.activate(new ActivationRequest(ACTIVATION_CODE, "device-00000002"));

        assertThat(response.valid()).isFalse();
        assertThat(response.status()).isEqualTo("INSTALLATION_LIMIT_REACHED");
        assertThat(license.getStatus()).isEqualTo(LicenseStatus.DRAFT);
        verify(installations, never()).save(any());
    }

    @Test
    void acceptsDuringConfiguredGraceAndExpiresAfterGrace() {
        License inGrace = license(NOW.minusSeconds(30), 60, 1);
        stubActivation(inGrace, 0);
        when(installations.existsByTokenHash(any(byte[].class))).thenReturn(false);
        when(installations.save(any(LicenseInstallation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicLicenseResponse graceResponse = service.activate(
                new ActivationRequest(ACTIVATION_CODE, "device-in-grace"));
        assertThat(graceResponse.valid()).isTrue();
        assertThat(graceResponse.graceUntil()).isEqualTo(NOW.plusSeconds(30));

        License expired = license(NOW.minusSeconds(61), 60, 1);
        when(licenses.findByActivationCodeHashForUpdate(any(byte[].class))).thenReturn(Optional.of(expired));

        PublicLicenseResponse expiredResponse = service.activate(
                new ActivationRequest(ACTIVATION_CODE, "device-expired"));
        assertThat(expiredResponse.valid()).isFalse();
        assertThat(expiredResponse.status()).isEqualTo("EXPIRED");
        assertThat(expired.getStatus()).isEqualTo(LicenseStatus.EXPIRED);
    }

    @Test
    void revokedLicenseAndInstallationFailClosed() {
        License license = license(NOW.plusSeconds(86_400), 0, 1);
        license.activate(NOW.minusSeconds(60));
        String token = "perat_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        LicenseInstallation installation = new LicenseInstallation(license.getCompanyId(), license.getId(),
                secrets.hashInstallationFingerprint("device-revoked"), secrets.hashInstallationToken(token),
                NOW.minusSeconds(60));
        license.revoke();
        installation.revoke(NOW);
        stubTokenLookup(license, installation);

        PublicLicenseResponse response = service.validate(new InstallationTokenRequest(token));

        assertThat(response.valid()).isFalse();
        assertThat(response.status()).isEqualTo("REVOKED");
        assertThat(response.companyId()).isEqualTo(license.getCompanyId());
        assertThat(response.features()).isEmpty();
        assertThat(response.nextCheckAt()).isNull();
    }

    @Test
    void rotatesInstallationTokenAndImmediatelyRejectsThePreviousToken() {
        License license = license(NOW.plusSeconds(86_400), 0, 1);
        license.activate(NOW.minusSeconds(60));
        String previousToken = "perat_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
        LicenseInstallation installation = new LicenseInstallation(license.getCompanyId(), license.getId(),
                secrets.hashInstallationFingerprint("device-rotate"),
                secrets.hashInstallationToken(previousToken), NOW.minusSeconds(60));
        stubTokenLookup(license, installation);
        when(installations.existsByTokenHash(any(byte[].class))).thenReturn(false);

        PublicLicenseResponse rotated = service.rotate(new InstallationTokenRequest(previousToken));

        assertThat(rotated.valid()).isTrue();
        assertThat(rotated.installationToken()).startsWith("perat_").isNotEqualTo(previousToken);
        assertThat(installation.tokenHashMatches(secrets.hashInstallationToken(previousToken))).isFalse();
        assertThat(installation.tokenHashMatches(secrets.hashInstallationToken(rotated.installationToken()))).isTrue();

        PublicLicenseResponse oldTokenResult = service.validate(new InstallationTokenRequest(previousToken));
        assertThat(oldTokenResult.valid()).isFalse();
        assertThat(oldTokenResult.status()).isEqualTo("INVALID");
    }

    @Test
    void unknownPublicSecretsReturnUniformFailClosedResponses() {
        when(licenses.findByActivationCodeHashForUpdate(any(byte[].class))).thenReturn(Optional.empty());
        when(installations.findByTokenHash(any(byte[].class))).thenReturn(Optional.empty());

        PublicLicenseResponse activation = service.activate(
                new ActivationRequest(ACTIVATION_CODE, "unknown-device"));
        PublicLicenseResponse validation = service.validate(new InstallationTokenRequest(
                "perat_unknown-token-abcdefghijklmnopqrstuvwxyz012345"));

        assertThat(activation).isEqualTo(PublicLicenseResponse.invalid("INVALID"));
        assertThat(validation).isEqualTo(PublicLicenseResponse.invalid("INVALID"));
    }

    private PublicLicenseService serviceAt(Instant instant) {
        return new PublicLicenseService(licenses, installations, secrets, Clock.fixed(instant, ZoneOffset.UTC));
    }

    private License license(Instant validUntil, long graceSeconds, int maximumInstallations) {
        return new License(UUID.randomUUID(), "Licencia", secrets.hashActivationCode(ACTIVATION_CODE),
                NOW.minusSeconds(3_600), validUntil, graceSeconds, maximumInstallations, 300,
                Set.of("sales", "reports.basic"));
    }

    private void stubActivation(License license, long activeInstallations) {
        when(licenses.findByActivationCodeHashForUpdate(any(byte[].class))).thenReturn(Optional.of(license));
        when(installations.findByFingerprintForUpdate(any(), any(byte[].class))).thenReturn(Optional.empty());
        when(installations.countByLicenseIdAndStatus(license.getId(), InstallationStatus.ACTIVE))
                .thenReturn(activeInstallations);
    }

    private void stubTokenLookup(License license, LicenseInstallation installation) {
        when(installations.findByTokenHash(any(byte[].class))).thenReturn(Optional.of(installation));
        when(licenses.findByIdForUpdate(license.getId())).thenReturn(Optional.of(license));
        when(installations.findByIdForUpdate(installation.getId(), license.getId(), license.getCompanyId()))
                .thenReturn(Optional.of(installation));
    }
}
