package com.peraerp.operations.logistics.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class FileSystemShipmentDocumentStorage implements ShipmentDocumentStorage {

    private final Path root;

    public FileSystemShipmentDocumentStorage(
            @Value("${pera.shipment-document.storage-root:.runtime/shipment-documents}") String storageRoot) {
        try {
            this.root = Path.of(storageRoot).toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new ShipmentDocumentStorageException("La raíz de documentos no es un directorio seguro.");
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ShipmentDocumentStorageException storageException) {
                throw storageException;
            }
            throw new ShipmentDocumentStorageException("No se pudo preparar el almacenamiento de documentos.", exception);
        }
    }

    @Override
    public StoredObject store(UUID companyId, UUID shipmentId, byte[] content) {
        Path directory = ensureShipmentDirectory(companyId, shipmentId);
        String objectName = UUID.randomUUID() + ".bin";
        Path target = directory.resolve(objectName).normalize();
        requireUnderRoot(target);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, ".upload-", ".tmp");
            if (Files.isSymbolicLink(temporary)) {
                throw new ShipmentDocumentStorageException("El destino temporal no es seguro.");
            }
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            String key = keyPrefix(companyId, shipmentId) + objectName;
            return new StoredObject(key, content.length);
        } catch (IOException exception) {
            throw new ShipmentDocumentStorageException("No se pudo almacenar el documento.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best effort: the committed object is authoritative and temporary names are never exposed.
                }
            }
        }
    }

    @Override
    public byte[] read(UUID companyId, UUID shipmentId, String storageKey, long maximumBytes) {
        if (maximumBytes <= 0) {
            throw new ShipmentDocumentStorageException("El límite de lectura debe ser positivo.");
        }
        Path object = resolveObject(companyId, shipmentId, storageKey);
        try {
            if (!Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(object)) {
                throw new ShipmentDocumentStorageException("El objeto de documento no existe o no es seguro.");
            }
            if (Files.size(object) > maximumBytes) {
                throw new ShipmentDocumentStorageException("El objeto supera el límite de lectura configurado.");
            }
            return Files.readAllBytes(object);
        } catch (IOException exception) {
            throw new ShipmentDocumentStorageException("No se pudo leer el documento.", exception);
        }
    }

    @Override
    public boolean exists(UUID companyId, UUID shipmentId, String storageKey) {
        Path object = resolveObject(companyId, shipmentId, storageKey);
        return Files.isRegularFile(object, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(object);
    }

    @Override
    public void delete(UUID companyId, UUID shipmentId, String storageKey) {
        Path object = resolveObject(companyId, shipmentId, storageKey);
        try {
            if (Files.isSymbolicLink(object)) {
                throw new ShipmentDocumentStorageException("No se permite borrar un enlace simbólico.");
            }
            Files.deleteIfExists(object);
        } catch (IOException exception) {
            throw new ShipmentDocumentStorageException("No se pudo eliminar el documento.", exception);
        }
    }

    private Path ensureShipmentDirectory(UUID companyId, UUID shipmentId) {
        Path current = root;
        current = ensureDirectory(current, "companies");
        current = ensureDirectory(current, companyId.toString());
        current = ensureDirectory(current, "shipments");
        return ensureDirectory(current, shipmentId.toString());
    }

    private Path ensureDirectory(Path parent, String segment) {
        Path candidate = parent.resolve(segment).normalize();
        requireUnderRoot(candidate);
        try {
            try {
                Files.createDirectory(candidate);
            } catch (FileAlreadyExistsException ignored) {
                // Validated below, including the symlink case.
            }
            if (Files.isSymbolicLink(candidate) || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                throw new ShipmentDocumentStorageException("La ruta de almacenamiento contiene un enlace no seguro.");
            }
            return candidate;
        } catch (IOException exception) {
            throw new ShipmentDocumentStorageException("No se pudo preparar el directorio del documento.", exception);
        }
    }

    private Path resolveObject(UUID companyId, UUID shipmentId, String storageKey) {
        String prefix = keyPrefix(companyId, shipmentId);
        if (storageKey == null || !storageKey.startsWith(prefix)) {
            throw new ShipmentDocumentStorageException("La clave no pertenece a la empresa y expedición activas.");
        }
        String objectName = storageKey.substring(prefix.length());
        if (objectName.contains("/") || objectName.contains("\\") || !objectName.endsWith(".bin")) {
            throw new ShipmentDocumentStorageException("La clave de documento no tiene un formato seguro.");
        }
        try {
            UUID.fromString(objectName.substring(0, objectName.length() - 4));
        } catch (IllegalArgumentException exception) {
            throw new ShipmentDocumentStorageException("La clave de documento no tiene un formato seguro.");
        }
        Path directory = root.resolve(prefix.replace('/', java.io.File.separatorChar)).normalize();
        requireUnderRoot(directory);
        assertExistingDirectoryPathIsSafe(directory);
        Path object = directory.resolve(objectName).normalize();
        requireUnderRoot(object);
        return object;
    }

    private void assertExistingDirectoryPathIsSafe(Path directory) {
        Path relative = root.relativize(directory);
        Path current = root;
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
                throw new ShipmentDocumentStorageException("La ruta de almacenamiento no es segura.");
            }
        }
    }

    private void requireUnderRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new ShipmentDocumentStorageException("La ruta del documento sale de la raíz configurada.");
        }
    }

    private String keyPrefix(UUID companyId, UUID shipmentId) {
        return "companies/" + companyId + "/shipments/" + shipmentId + "/";
    }
}
