package com.peraerp.identity.company.logo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemCompanyLogoStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesReadsAndDeletesOnlyLogicalTenantScopedObjects() {
        FileSystemCompanyLogoStorage storage = storage();
        UUID companyId = UUID.randomUUID();
        byte[] content = new byte[]{1, 2, 3, 4};

        String key = storage.store(companyId, CompanyLogoMediaType.PNG, content);

        assertThat(key).startsWith("companies/" + companyId + "/logos/").endsWith(".png");
        assertThat(storage.read(companyId, key)).containsExactly(content);
        storage.delete(companyId, key);
        storage.delete(companyId, key);
        assertThatThrownBy(() -> storage.read(companyId, key))
                .isInstanceOf(CompanyLogoStorageException.class);
    }

    @Test
    void rejectsTraversalForeignTenantAndUnexpectedExtensions() {
        FileSystemCompanyLogoStorage storage = storage();
        UUID companyId = UUID.randomUUID();
        UUID foreignCompanyId = UUID.randomUUID();

        assertThatThrownBy(() -> storage.read(companyId,
                "companies/" + companyId + "/logos/../outside.png"))
                .isInstanceOf(CompanyLogoStorageException.class);
        assertThatThrownBy(() -> storage.read(companyId,
                "companies/" + foreignCompanyId + "/logos/logo.png"))
                .isInstanceOf(CompanyLogoStorageException.class)
                .hasMessageContaining("empresa activa");
        assertThatThrownBy(() -> storage.read(companyId,
                "companies/" + companyId + "/logos/logo.svg"))
                .isInstanceOf(CompanyLogoStorageException.class);
    }

    @Test
    void refusesSymbolicLinksInsteadOfFollowingThem() throws IOException {
        FileSystemCompanyLogoStorage storage = storage();
        UUID companyId = UUID.randomUUID();
        Path logoDirectory = temporaryDirectory.resolve("root/companies/" + companyId + "/logos");
        Files.createDirectories(logoDirectory);
        Path outside = temporaryDirectory.resolve("outside.png");
        Files.write(outside, new byte[]{1, 2, 3});
        Path link = logoDirectory.resolve("linked.png");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("Symbolic links are unavailable in this environment.");
        }

        assertThatThrownBy(() -> storage.read(companyId,
                "companies/" + companyId + "/logos/linked.png"))
                .isInstanceOf(CompanyLogoStorageException.class)
                .hasMessageContaining("seguro");
    }

    private FileSystemCompanyLogoStorage storage() {
        return new FileSystemCompanyLogoStorage(
                new CompanyLogoProperties(temporaryDirectory.resolve("root").toString()));
    }
}
