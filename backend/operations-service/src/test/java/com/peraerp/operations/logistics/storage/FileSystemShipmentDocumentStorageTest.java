package com.peraerp.operations.logistics.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemShipmentDocumentStorageTest {

    @TempDir Path temporaryDirectory;

    @Test
    void createsOpaqueTenantScopedObjectsAndDeletesIdempotently() {
        FileSystemShipmentDocumentStorage storage =
                new FileSystemShipmentDocumentStorage(temporaryDirectory.toString());
        UUID companyId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        byte[] content = "document".getBytes(StandardCharsets.UTF_8);

        var stored = storage.store(companyId, shipmentId, content);

        assertThat(stored.storageKey()).startsWith(
                "companies/" + companyId + "/shipments/" + shipmentId + "/");
        assertThat(stored.storageKey()).endsWith(".bin");
        assertThat(stored.storageKey()).doesNotContain("document");
        assertThat(storage.read(companyId, shipmentId, stored.storageKey(), 100)).isEqualTo(content);
        storage.delete(companyId, shipmentId, stored.storageKey());
        storage.delete(companyId, shipmentId, stored.storageKey());
        assertThat(storage.exists(companyId, shipmentId, stored.storageKey())).isFalse();
    }

    @Test
    void rejectsForeignTenantAndTraversalKeys() {
        FileSystemShipmentDocumentStorage storage =
                new FileSystemShipmentDocumentStorage(temporaryDirectory.toString());
        UUID companyId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        var stored = storage.store(companyId, shipmentId, new byte[]{1});

        assertThatThrownBy(() -> storage.read(UUID.randomUUID(), shipmentId, stored.storageKey(), 100))
                .isInstanceOf(ShipmentDocumentStorageException.class)
                .hasMessageContaining("empresa");
        assertThatThrownBy(() -> storage.read(companyId, shipmentId,
                "companies/" + companyId + "/shipments/" + shipmentId + "/../secret.bin", 100))
                .isInstanceOf(ShipmentDocumentStorageException.class);
    }

    @Test
    void checksTheConfiguredLimitBeforeReadingTheObject() {
        FileSystemShipmentDocumentStorage storage =
                new FileSystemShipmentDocumentStorage(temporaryDirectory.toString());
        UUID companyId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        var stored = storage.store(companyId, shipmentId, new byte[8]);

        assertThatThrownBy(() -> storage.read(companyId, shipmentId, stored.storageKey(), 7))
                .isInstanceOf(ShipmentDocumentStorageException.class)
                .hasMessageContaining("límite");
    }

    @Test
    void refusesSymlinkedShipmentDirectoryWhenSupported() throws Exception {
        FileSystemShipmentDocumentStorage storage =
                new FileSystemShipmentDocumentStorage(temporaryDirectory.toString());
        UUID companyId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        var initial = storage.store(companyId, shipmentId, new byte[]{1});
        storage.delete(companyId, shipmentId, initial.storageKey());
        Path shipmentDirectory = temporaryDirectory.resolve("companies").resolve(companyId.toString())
                .resolve("shipments").resolve(shipmentId.toString());
        Files.delete(shipmentDirectory);
        Path external = Files.createDirectory(temporaryDirectory.resolve("external"));
        try {
            Files.createSymbolicLink(shipmentDirectory, external);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            return;
        }

        assertThatThrownBy(() -> storage.store(companyId, shipmentId, new byte[]{2}))
                .isInstanceOf(ShipmentDocumentStorageException.class)
                .hasMessageContaining("enlace");
    }
}
