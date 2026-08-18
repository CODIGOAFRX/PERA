package com.peraerp.identity.company.logo;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class FileSystemCompanyLogoStorage implements CompanyLogoStorage {

    private static final long MAX_STORED_BYTES = 2L * 1024 * 1024;
    private static final Pattern SAFE_FILE_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.(?:png|jpg|jpeg|webp)");

    private final Path root;

    public FileSystemCompanyLogoStorage(CompanyLogoProperties properties) {
        this.root = Path.of(properties.storageRoot()).toAbsolutePath().normalize();
        initializeRoot();
    }

    @Override
    public String store(UUID companyId, CompanyLogoMediaType mediaType, byte[] content) {
        if (content == null || content.length == 0 || content.length > MAX_STORED_BYTES) {
            throw new CompanyLogoStorageException("No se pudo almacenar el logo de empresa.");
        }
        Path logoDirectory = companyLogoDirectory(companyId);
        String fileName = UUID.randomUUID() + "." + mediaType.extension();
        String storageKey = "companies/" + companyId + "/logos/" + fileName;
        Path target = resolve(companyId, storageKey);
        Path temporary = logoDirectory.resolve("." + UUID.randomUUID() + ".tmp");
        try {
            ensureSafeDirectory(logoDirectory);
            try (FileChannel channel = FileChannel.open(temporary,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            return storageKey;
        } catch (IOException | SecurityException exception) {
            deleteTemporaryQuietly(temporary);
            throw new CompanyLogoStorageException("No se pudo almacenar el logo de empresa.", exception);
        }
    }

    @Override
    public byte[] read(UUID companyId, String storageKey) {
        Path logo = resolve(companyId, storageKey);
        ensureSafeExistingFile(logo);
        try (FileChannel channel = FileChannel.open(logo, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            long size = channel.size();
            if (size <= 0 || size > MAX_STORED_BYTES) {
                throw new CompanyLogoStorageException("El logo almacenado no es válido.");
            }
            ByteBuffer buffer = ByteBuffer.allocate(Math.toIntExact(size));
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                // Continue until the exact file snapshot has been consumed.
            }
            if (buffer.hasRemaining()) {
                throw new CompanyLogoStorageException("El logo almacenado no se pudo leer por completo.");
            }
            return buffer.array();
        } catch (IOException | ArithmeticException | SecurityException exception) {
            throw new CompanyLogoStorageException("No se pudo leer el logo de empresa.", exception);
        }
    }

    @Override
    public void delete(UUID companyId, String storageKey) {
        Path logo = resolve(companyId, storageKey);
        try {
            if (!Files.exists(logo, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            ensureSafeExistingFile(logo);
            Files.deleteIfExists(logo);
        } catch (IOException | SecurityException exception) {
            throw new CompanyLogoStorageException("No se pudo eliminar el logo de empresa.", exception);
        }
    }

    private void initializeRoot() {
        try {
            ensureNoSymbolicLinksInExistingPath(root);
            Files.createDirectories(root);
            ensureSafeDirectory(root);
        } catch (IOException | SecurityException exception) {
            throw new CompanyLogoStorageException("No se pudo inicializar el almacenamiento de logos.", exception);
        }
    }

    private Path companyLogoDirectory(UUID companyId) {
        Path directory = root.resolve("companies").resolve(companyId.toString()).resolve("logos").normalize();
        if (!directory.startsWith(root)) {
            throw new CompanyLogoStorageException("La clave del logo no es válida.");
        }
        try {
            ensureNoSymbolicLinksInExistingPath(directory);
            Files.createDirectories(directory);
            ensureSafeDirectory(directory);
            return directory;
        } catch (IOException | SecurityException exception) {
            throw new CompanyLogoStorageException("No se pudo preparar el almacenamiento del logo.", exception);
        }
    }

    private Path resolve(UUID companyId, String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\")
                || storageKey.contains("..") || storageKey.contains("\r") || storageKey.contains("\n")) {
            throw new CompanyLogoStorageException("La clave del logo no es válida.");
        }
        String prefix = "companies/" + companyId + "/logos/";
        if (!storageKey.startsWith(prefix)) {
            throw new CompanyLogoStorageException("La clave del logo no pertenece a la empresa activa.");
        }
        String fileName = storageKey.substring(prefix.length());
        if (fileName.contains("/") || !SAFE_FILE_NAME.matcher(fileName).matches()) {
            throw new CompanyLogoStorageException("La clave del logo no es válida.");
        }
        Path candidate = root.resolve("companies").resolve(companyId.toString()).resolve("logos")
                .resolve(fileName).normalize();
        Path expectedDirectory = root.resolve("companies").resolve(companyId.toString()).resolve("logos").normalize();
        if (!candidate.getParent().equals(expectedDirectory) || !candidate.startsWith(root)) {
            throw new CompanyLogoStorageException("La clave del logo no es válida.");
        }
        ensureNoSymbolicLinksInExistingPath(candidate.getParent());
        return candidate;
    }

    private void ensureSafeDirectory(Path directory) throws IOException {
        ensureNoSymbolicLinksInExistingPath(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Storage directory is not a regular directory.");
        }
    }

    private void ensureSafeExistingFile(Path file) {
        ensureNoSymbolicLinksInExistingPath(file.getParent());
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new CompanyLogoStorageException("El logo almacenado no es un archivo seguro.");
        }
    }

    private void ensureNoSymbolicLinksInExistingPath(Path path) {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current == null ? component : current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new CompanyLogoStorageException("El almacenamiento contiene un enlace simbólico no permitido.");
            }
        }
    }

    private void deleteTemporaryQuietly(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // The original storage exception remains authoritative.
        }
    }
}
