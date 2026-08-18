package com.peraerp.identity.company;

import com.peraerp.identity.company.logo.CompanyLogoMediaType;
import com.peraerp.identity.company.logo.CompanyLogoStorage;
import com.peraerp.identity.company.logo.CompanyLogoStorageException;
import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyLogoServiceTest {

    @Mock CompanySettingsRepository settingsRepository;
    @Mock CompanyRepository companyRepository;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock CompanyLogoStorage storage;

    private CompanyLogoService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new CompanyLogoService(settingsRepository, companyRepository, companyProvider, storage);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void replacesLogoOnlyAfterTheNewMetadataHasBeenFlushed() {
        CompanySettings settings = settingsWithLogo("old.png", "image/png", "b".repeat(64));
        String newKey = key("new.png");
        byte[] png = png();
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));
        when(storage.store(eq(companyId), eq(CompanyLogoMediaType.PNG), any(byte[].class))).thenReturn(newKey);
        when(settingsRepository.saveAndFlush(settings)).thenReturn(settings);

        CompanySettingsResponse response = service.uploadCurrent(
                new MockMultipartFile("file", "logo.png", "image/png", png));

        assertThat(response.logoStorageKey()).isEqualTo(newKey);
        assertThat(response.logoContentType()).isEqualTo("image/png");
        assertThat(response.logoSha256()).isEqualTo(sha256(png));
        InOrder ordered = inOrder(settingsRepository, storage);
        ordered.verify(settingsRepository).findByCompanyId(companyId);
        ordered.verify(storage).store(eq(companyId), eq(CompanyLogoMediaType.PNG), any(byte[].class));
        ordered.verify(settingsRepository).saveAndFlush(settings);
        ordered.verify(storage).delete(companyId, key("old.png"));
    }

    @Test
    void oldObjectCleanupWaitsUntilTheDatabaseTransactionCommits() {
        CompanySettings settings = settingsWithLogo("old.png", "image/png", "f".repeat(64));
        String newKey = key("new.png");
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));
        when(storage.store(eq(companyId), eq(CompanyLogoMediaType.PNG), any(byte[].class))).thenReturn(newKey);
        when(settingsRepository.saveAndFlush(settings)).thenReturn(settings);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.uploadCurrent(new MockMultipartFile("file", "logo.png", "image/png", png()));
            verify(storage, never()).delete(companyId, key("old.png"));

            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }

            verify(storage).delete(companyId, key("old.png"));
            verify(storage, never()).delete(companyId, newKey);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void rejectsMimeSpoofingSvgAndOversizedFilesBeforeStorage() {
        CompanySettings settings = CompanySettings.defaults(companyId, "PERA");
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> service.uploadCurrent(
                new MockMultipartFile("file", "spoof.png", "image/png", jpeg())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Content-Type");

        assertThatThrownBy(() -> service.uploadCurrent(
                new MockMultipartFile("file", "logo.svg", "image/svg+xml", "<svg/>".getBytes())))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PNG, JPEG o WebP");

        assertThatThrownBy(() -> service.uploadCurrent(new MockMultipartFile(
                "file", "large.png", "image/png", new byte[(int) CompanyLogoService.MAX_LOGO_BYTES + 1])))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 MiB");

        verify(storage, never()).store(any(), any(), any());
    }

    @Test
    void storageFailureDoesNotChangeExistingMetadata() {
        CompanySettings settings = settingsWithLogo("old.png", "image/png", "c".repeat(64));
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));
        when(storage.store(eq(companyId), eq(CompanyLogoMediaType.PNG), any(byte[].class)))
                .thenThrow(new CompanyLogoStorageException("storage unavailable"));

        assertThatThrownBy(() -> service.uploadCurrent(
                new MockMultipartFile("file", "logo.png", "image/png", png())))
                .isInstanceOf(CompanyLogoStorageException.class);

        assertThat(settings.getLogoStorageKey()).isEqualTo(key("old.png"));
        assertThat(settings.getLogoSha256()).isEqualTo("c".repeat(64));
        verify(settingsRepository, never()).saveAndFlush(any());
    }

    @Test
    void storageCannotReturnAKeyFromAnotherTenant() {
        CompanySettings settings = settingsWithLogo("old.png", "image/png", "c".repeat(64));
        String foreignKey = "companies/" + UUID.randomUUID() + "/logos/new.png";
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));
        when(storage.store(eq(companyId), eq(CompanyLogoMediaType.PNG), any(byte[].class)))
                .thenReturn(foreignKey);

        assertThatThrownBy(() -> service.uploadCurrent(
                new MockMultipartFile("file", "logo.png", "image/png", png())))
                .isInstanceOf(CompanyLogoStorageException.class)
                .hasMessageContaining("clave");

        assertThat(settings.getLogoStorageKey()).isEqualTo(key("old.png"));
        verify(settingsRepository, never()).saveAndFlush(any());
        verify(storage).delete(companyId, foreignKey);
    }

    @Test
    void persistenceFailureRestoresMetadataAndRemovesTheUncommittedObject() {
        CompanySettings settings = settingsWithLogo("old.png", "image/png", "d".repeat(64));
        String newKey = key("new.png");
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));
        when(storage.store(eq(companyId), eq(CompanyLogoMediaType.PNG), any(byte[].class))).thenReturn(newKey);
        when(settingsRepository.saveAndFlush(settings)).thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> service.uploadCurrent(
                new MockMultipartFile("file", "logo.png", "image/png", png())))
                .isInstanceOf(IllegalStateException.class);

        assertThat(settings.getLogoStorageKey()).isEqualTo(key("old.png"));
        assertThat(settings.getLogoSha256()).isEqualTo("d".repeat(64));
        verify(storage).delete(companyId, newKey);
        verify(storage, never()).delete(companyId, key("old.png"));
    }

    @Test
    void deletionIsIdempotentAndClearsMetadataBeforeTouchingStorage() {
        CompanySettings settings = settingsWithLogo("old.png", "image/png", "e".repeat(64));
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));
        when(settingsRepository.saveAndFlush(settings)).thenReturn(settings);

        service.deleteCurrent();
        service.deleteCurrent();

        assertThat(settings.getLogoStorageKey()).isNull();
        assertThat(settings.getLogoContentType()).isNull();
        assertThat(settings.getLogoSha256()).isNull();
        verify(settingsRepository).saveAndFlush(settings);
        verify(storage).delete(companyId, key("old.png"));
    }

    @Test
    void matchingEtagAvoidsReadingTheFile() {
        byte[] png = png();
        String sha = sha256(png);
        CompanySettings settings = settingsWithLogo("current.png", "image/png", sha);
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));

        CompanyLogoService.CompanyLogoDownload result = service.downloadCurrent("W/\"" + sha + "\"");

        assertThat(result.notModified()).isTrue();
        assertThat(result.etag()).isEqualTo("\"" + sha + "\"");
        assertThat(result.content()).isNull();
        verify(storage, never()).read(any(), any());
    }

    @Test
    void storedBytesAreCheckedAgainstHashAndMimeMetadata() {
        byte[] png = png();
        CompanySettings settings = settingsWithLogo("current.png", "image/png", sha256(png));
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));
        when(storage.read(companyId, key("current.png"))).thenReturn(png);

        CompanyLogoService.CompanyLogoDownload result = service.downloadCurrent(null);

        assertThat(result.notModified()).isFalse();
        assertThat(result.content()).containsExactly(png);
        assertThat(result.contentType()).isEqualTo("image/png");
    }

    private CompanySettings settingsWithLogo(String fileName, String contentType, String sha) {
        CompanySettings settings = CompanySettings.defaults(companyId, "PERA");
        settings.updateLogo(key(fileName), contentType, sha);
        return settings;
    }

    private String key(String fileName) {
        return "companies/" + companyId + "/logos/" + fileName;
    }

    private byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
    }

    private byte[] jpeg() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3};
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
