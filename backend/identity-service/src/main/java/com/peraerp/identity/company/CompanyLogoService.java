package com.peraerp.identity.company;

import com.peraerp.identity.company.logo.CompanyLogoMediaType;
import com.peraerp.identity.company.logo.CompanyLogoStorage;
import com.peraerp.identity.company.logo.CompanyLogoStorageException;
import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class CompanyLogoService {

    static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(CompanyLogoService.class);

    private final CompanySettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;
    private final CurrentCompanyProvider companyProvider;
    private final CompanyLogoStorage storage;

    public CompanyLogoService(CompanySettingsRepository settingsRepository, CompanyRepository companyRepository,
                              CurrentCompanyProvider companyProvider, CompanyLogoStorage storage) {
        this.settingsRepository = settingsRepository;
        this.companyRepository = companyRepository;
        this.companyProvider = companyProvider;
        this.storage = storage;
    }

    @Transactional
    public CompanySettingsResponse uploadCurrent(MultipartFile file) {
        UUID companyId = companyProvider.requireCompanyId();
        CompanySettings settings = requireOrCreate(companyId);
        ValidatedLogo logo = validate(file);
        LogoMetadata previous = LogoMetadata.from(settings);
        String newStorageKey = storage.store(companyId, logo.mediaType(), logo.content().clone());
        try {
            validateGeneratedStorageKey(companyId, newStorageKey);
        } catch (RuntimeException exception) {
            if (newStorageKey != null) {
                deleteQuietly(companyId, newStorageKey);
            }
            throw exception;
        }
        boolean synchronizedTransaction = hasSynchronizedTransaction();
        if (synchronizedTransaction) {
            registerReplacementCleanup(companyId, newStorageKey, previous.storageKey());
        }
        try {
            settings.updateLogo(newStorageKey, logo.mediaType().contentType(), logo.sha256());
            settingsRepository.saveAndFlush(settings);
        } catch (RuntimeException exception) {
            settings.updateLogo(previous.storageKey(), previous.contentType(), previous.sha256());
            if (!synchronizedTransaction) {
                deleteQuietly(companyId, newStorageKey);
            }
            throw exception;
        }
        if (!synchronizedTransaction && previous.storageKey() != null) {
            deleteQuietly(companyId, previous.storageKey());
        }
        return CompanySettingsResponse.from(settings);
    }

    @Transactional
    public void deleteCurrent() {
        UUID companyId = companyProvider.requireCompanyId();
        CompanySettings settings = requireOrCreate(companyId);
        LogoMetadata previous = LogoMetadata.from(settings);
        if (previous.storageKey() == null) {
            return;
        }
        try {
            settings.clearLogo();
            settingsRepository.saveAndFlush(settings);
        } catch (RuntimeException exception) {
            settings.updateLogo(previous.storageKey(), previous.contentType(), previous.sha256());
            throw exception;
        }
        if (hasSynchronizedTransaction()) {
            registerDeletionAfterCommit(companyId, previous.storageKey());
        } else {
            deleteQuietly(companyId, previous.storageKey());
        }
    }

    @Transactional
    public CompanyLogoDownload downloadCurrent(String ifNoneMatch) {
        UUID companyId = companyProvider.requireCompanyId();
        CompanySettings settings = requireOrCreate(companyId);
        if (settings.getLogoStorageKey() == null) {
            throw new ResourceNotFoundException("Logo de empresa", companyId);
        }
        String etag = '"' + settings.getLogoSha256().toLowerCase(Locale.ROOT) + '"';
        if (etagMatches(ifNoneMatch, etag)) {
            return CompanyLogoDownload.notModified(settings.getLogoContentType(), settings.getLogoSha256(), etag);
        }
        byte[] content = storage.read(companyId, settings.getLogoStorageKey());
        String actualHash = sha256(content);
        CompanyLogoMediaType actualType = detectMediaType(content);
        if (!actualHash.equalsIgnoreCase(settings.getLogoSha256())
                || !actualType.contentType().equals(settings.getLogoContentType())) {
            throw new CompanyLogoStorageException("El logo almacenado no coincide con su metadata segura.");
        }
        return CompanyLogoDownload.content(settings.getLogoContentType(), settings.getLogoSha256(), etag, content);
    }

    private CompanySettings requireOrCreate(UUID companyId) {
        return settingsRepository.findByCompanyId(companyId).orElseGet(() -> {
            Company company = companyRepository.findById(companyId)
                    .filter(Company::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa", companyId));
            return settingsRepository.save(CompanySettings.defaults(companyId, company.getName()));
        });
    }

    private ValidatedLogo validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("El logo no puede estar vacío.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new BusinessRuleException("El logo no puede superar 2 MiB.");
        }
        String declaredContentType = file.getContentType() == null
                ? "" : file.getContentType().trim().toLowerCase(Locale.ROOT);
        CompanyLogoMediaType declaredType = Arrays.stream(CompanyLogoMediaType.values())
                .filter(type -> type.contentType().equals(declaredContentType))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Solo se admiten logos PNG, JPEG o WebP."));
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessRuleException("No se pudo leer el logo recibido.", exception);
        }
        if (content.length == 0 || content.length > MAX_LOGO_BYTES) {
            throw new BusinessRuleException("El logo no puede estar vacío ni superar 2 MiB.");
        }
        CompanyLogoMediaType detectedType = detectMediaType(content);
        if (detectedType != declaredType) {
            throw new BusinessRuleException("El contenido real del logo no coincide con su Content-Type.");
        }
        return new ValidatedLogo(detectedType, content, sha256(content));
    }

    private CompanyLogoMediaType detectMediaType(byte[] content) {
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 0x50
                && content[2] == 0x4e && content[3] == 0x47 && content[4] == 0x0d
                && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a) {
            return CompanyLogoMediaType.PNG;
        }
        if (content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return CompanyLogoMediaType.JPEG;
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I'
                && content[2] == 'F' && content[3] == 'F' && content[8] == 'W'
                && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return CompanyLogoMediaType.WEBP;
        }
        throw new BusinessRuleException("El archivo no contiene una firma PNG, JPEG o WebP válida.");
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", exception);
        }
    }

    private void validateGeneratedStorageKey(UUID companyId, String storageKey) {
        String prefix = "companies/" + companyId + "/logos/";
        if (storageKey == null || storageKey.length() > 500 || !storageKey.startsWith(prefix)
                || storageKey.length() == prefix.length() || storageKey.contains("..")
                || storageKey.contains("\\") || storageKey.contains("\r") || storageKey.contains("\n")) {
            throw new CompanyLogoStorageException("El almacenamiento devolvió una clave de logo no válida.");
        }
    }

    private boolean etagMatches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalized = candidate.trim();
            if (normalized.equals("*")) {
                return true;
            }
            if (normalized.startsWith("W/")) {
                normalized = normalized.substring(2).trim();
            }
            if (normalized.equals(etag)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSynchronizedTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private void registerReplacementCleanup(UUID companyId, String newStorageKey, String previousStorageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (previousStorageKey != null) {
                    deleteQuietly(companyId, previousStorageKey);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(companyId, newStorageKey);
                }
            }
        });
    }

    private void registerDeletionAfterCommit(UUID companyId, String storageKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(companyId, storageKey);
            }
        });
    }

    private void deleteQuietly(UUID companyId, String storageKey) {
        try {
            storage.delete(companyId, storageKey);
        } catch (CompanyLogoStorageException exception) {
            LOGGER.warn("No se pudo limpiar un objeto de logo obsoleto de forma automática.");
        }
    }

    private record LogoMetadata(String storageKey, String contentType, String sha256) {
        static LogoMetadata from(CompanySettings settings) {
            return new LogoMetadata(settings.getLogoStorageKey(), settings.getLogoContentType(),
                    settings.getLogoSha256());
        }
    }

    private record ValidatedLogo(CompanyLogoMediaType mediaType, byte[] content, String sha256) {
    }

    public record CompanyLogoDownload(String contentType, String sha256, String etag,
                                      byte[] content, boolean notModified) {
        public CompanyLogoDownload {
            content = content == null ? null : content.clone();
        }

        @Override
        public byte[] content() {
            return content == null ? null : content.clone();
        }

        static CompanyLogoDownload notModified(String contentType, String sha256, String etag) {
            return new CompanyLogoDownload(contentType, sha256, etag, null, true);
        }

        static CompanyLogoDownload content(String contentType, String sha256, String etag, byte[] content) {
            return new CompanyLogoDownload(contentType, sha256, etag, content, false);
        }
    }
}
