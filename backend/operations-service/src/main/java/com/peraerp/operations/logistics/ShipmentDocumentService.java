package com.peraerp.operations.logistics;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.logistics.storage.ShipmentDocumentStorage;
import com.peraerp.operations.logistics.storage.ShipmentDocumentStorageException;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentDocumentRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentDocumentResponse;

@Service
public class ShipmentDocumentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShipmentDocumentService.class);
    private static final Pattern DOCUMENT_TYPE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$");
    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/webp",
            "text/plain", "text/csv", "application/json", "application/xml", "text/xml");

    private final ShipmentRepository shipmentRepository;
    private final ShipmentDocumentRepository repository;
    private final ShipmentDocumentStorage storage;
    private final CurrentCompanyProvider companyProvider;
    private final long maximumBytes;

    public ShipmentDocumentService(ShipmentRepository shipmentRepository, ShipmentDocumentRepository repository,
                                   ShipmentDocumentStorage storage, CurrentCompanyProvider companyProvider,
                                   @Value("${pera.shipment-document.max-bytes:10485760}") long maximumBytes) {
        this.shipmentRepository = shipmentRepository;
        this.repository = repository;
        this.storage = storage;
        this.companyProvider = companyProvider;
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("pera.shipment-document.max-bytes debe ser positivo");
        }
        this.maximumBytes = maximumBytes;
    }

    @Transactional
    public ShipmentDocumentResponse upload(UUID shipmentId, String documentType, MultipartFile file) {
        Shipment shipment = requireMutableShipment(shipmentId);
        validateDocumentType(documentType);
        String originalFileName = validateOriginalFileName(file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new BusinessRuleException("El documento no puede estar vacío.");
        }
        if (file.getSize() > maximumBytes) {
            throw new BusinessRuleException("El documento supera el límite de " + maximumBytes + " bytes.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ShipmentDocumentStorageException("No se pudo leer el documento recibido.", exception);
        }
        ValidatedContent validated = validateContent(content, file.getContentType());
        ShipmentDocumentStorage.StoredObject stored = storage.store(
                shipment.getCompanyId(), shipmentId, validated.content());
        registerRollbackCleanup(shipment.getCompanyId(), shipmentId, stored.storageKey());
        ShipmentDocument document = new ShipmentDocument(shipment.getCompanyId(), shipmentId,
                normalizeDocumentType(documentType), originalFileName, stored.storageKey(), validated.mediaType(),
                validated.sha256(), stored.sizeBytes());
        try {
            return ShipmentDocumentResponse.from(repository.saveAndFlush(document));
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                safeDelete(shipment.getCompanyId(), shipmentId, stored.storageKey());
            }
            throw exception;
        }
    }

    @Transactional
    ShipmentDocumentResponse registerMetadata(UUID shipmentId, ShipmentDocumentRequest request) {
        Shipment shipment = requireMutableShipment(shipmentId);
        validateDocumentType(request.documentType());
        String originalFileName = validateOriginalFileName(request.originalFileName());
        if (request.sizeBytes() > maximumBytes) {
            throw new BusinessRuleException("El documento supera el límite configurado.");
        }
        String storageKey = request.storageKey().trim();
        try {
            if (!storage.exists(shipment.getCompanyId(), shipmentId, storageKey)) {
                throw new BusinessRuleException("La clave no identifica un objeto almacenado.");
            }
        } catch (ShipmentDocumentStorageException exception) {
            throw new BusinessRuleException("La clave de almacenamiento no es válida.", exception);
        }
        byte[] content = storage.read(shipment.getCompanyId(), shipmentId, storageKey, maximumBytes);
        ValidatedContent validated = validateContent(content, request.mediaType());
        if (validated.content().length != request.sizeBytes()
                || !validated.sha256().equalsIgnoreCase(request.sha256().trim())) {
            throw new BusinessRuleException("El tamaño o checksum no coincide con el objeto almacenado.");
        }
        if (repository.existsByCompanyIdAndShipmentIdAndStorageKey(
                shipment.getCompanyId(), shipmentId, storageKey)) {
            throw new BusinessRuleException("Ya existe un documento con esa clave de almacenamiento.");
        }
        ShipmentDocument document = new ShipmentDocument(shipment.getCompanyId(), shipmentId,
                normalizeDocumentType(request.documentType()), originalFileName, storageKey, validated.mediaType(),
                validated.sha256(), validated.content().length);
        return ShipmentDocumentResponse.from(repository.save(document));
    }

    @Transactional(readOnly = true)
    public ShipmentDocumentDownload download(UUID shipmentId, UUID documentId) {
        UUID companyId = companyProvider.requireCompanyId();
        requireShipment(shipmentId, companyId);
        ShipmentDocument document = repository.findByIdAndCompanyIdAndShipmentId(documentId, companyId, shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de envío", documentId));
        byte[] content = storage.read(companyId, shipmentId, document.getStorageKey(), maximumBytes);
        if (content.length != document.getSizeBytes() || !sha256(content).equals(document.getSha256())) {
            throw new ShipmentDocumentStorageException(
                    "El objeto almacenado no coincide con la metadata y no se entregará.");
        }
        return new ShipmentDocumentDownload(document, content);
    }

    @Transactional
    public void delete(UUID shipmentId, UUID documentId) {
        Shipment shipment = requireMutableShipment(shipmentId);
        ShipmentDocument document = repository
                .findByIdAndCompanyIdAndShipmentId(documentId, shipment.getCompanyId(), shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de envío", documentId));
        repository.delete(document);
        repository.flush();
        scheduleDeleteAfterCommit(shipment.getCompanyId(), shipmentId, document.getStorageKey());
    }

    @Transactional
    public void deleteAllForShipment(UUID companyId, UUID shipmentId) {
        List<ShipmentDocument> documents = repository
                .findAllByCompanyIdAndShipmentIdOrderByCreatedAtAsc(companyId, shipmentId);
        repository.deleteAllByCompanyIdAndShipmentId(companyId, shipmentId);
        repository.flush();
        documents.forEach(document -> scheduleDeleteAfterCommit(companyId, shipmentId, document.getStorageKey()));
    }

    private Shipment requireMutableShipment(UUID shipmentId) {
        UUID companyId = companyProvider.requireCompanyId();
        Shipment shipment = requireShipment(shipmentId, companyId);
        if (shipment.getStatus() == ShipmentStatus.CANCELLED) {
            throw new BusinessRuleException("No se pueden modificar los documentos de un envío cancelado.");
        }
        return shipment;
    }

    private Shipment requireShipment(UUID shipmentId, UUID companyId) {
        return shipmentRepository.findByIdAndCompanyId(shipmentId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", shipmentId));
    }

    private ValidatedContent validateContent(byte[] content, String declaredMediaType) {
        if (content.length == 0) {
            throw new BusinessRuleException("El documento no puede estar vacío.");
        }
        if (content.length > maximumBytes) {
            throw new BusinessRuleException("El documento supera el límite de " + maximumBytes + " bytes.");
        }
        String mediaType = normalizeMediaType(declaredMediaType);
        if (!ALLOWED_MEDIA_TYPES.contains(mediaType)) {
            throw new BusinessRuleException("El tipo de documento no está permitido.");
        }
        boolean valid = switch (mediaType) {
            case "application/pdf" -> startsWith(content, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "image/png" -> startsWith(content,
                    new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
            case "image/jpeg" -> content.length >= 3 && (content[0] & 0xff) == 0xff
                    && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff;
            case "image/webp" -> content.length >= 12
                    && startsWith(content, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && Arrays.equals(Arrays.copyOfRange(content, 8, 12),
                    "WEBP".getBytes(StandardCharsets.US_ASCII));
            case "application/json" -> textualContentStartsWith(content, "{", "[");
            case "application/xml", "text/xml" -> textualContentStartsWith(content, "<");
            case "text/plain", "text/csv" -> isSafeUtf8Text(content);
            default -> false;
        };
        if (!valid) {
            throw new BusinessRuleException("El contenido real no coincide con el tipo MIME declarado.");
        }
        return new ValidatedContent(content, mediaType, sha256(content));
    }

    private boolean textualContentStartsWith(byte[] content, String... prefixes) {
        String text = decodeUtf8(content);
        String normalized = text.startsWith("\ufeff") ? text.substring(1) : text;
        normalized = normalized.stripLeading();
        return Arrays.stream(prefixes).anyMatch(normalized::startsWith) && containsOnlySafeTextCharacters(text);
    }

    private boolean isSafeUtf8Text(byte[] content) {
        return containsOnlySafeTextCharacters(decodeUtf8(content));
    }

    private String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            throw new BusinessRuleException("El documento de texto no contiene UTF-8 válido.");
        }
    }

    private boolean containsOnlySafeTextCharacters(String text) {
        return text.codePoints().noneMatch(codePoint -> codePoint == 0
                || (Character.isISOControl(codePoint) && codePoint != '\r' && codePoint != '\n' && codePoint != '\t'));
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
    }

    private String validateOriginalFileName(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new BusinessRuleException("El nombre original del documento no es válido.");
        }
        String name = value.trim();
        if (name.contains("/") || name.contains("\\") || name.contains("\r") || name.contains("\n")
                || name.equals(".") || name.equals("..")) {
            throw new BusinessRuleException("El nombre original del documento no es válido.");
        }
        return name;
    }

    private void validateDocumentType(String value) {
        if (value == null || !DOCUMENT_TYPE.matcher(value.trim()).matches()) {
            throw new BusinessRuleException("El tipo documental no es válido.");
        }
    }

    private String normalizeDocumentType(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeMediaType(String value) {
        if (value == null) {
            return "";
        }
        int parameters = value.indexOf(';');
        return (parameters < 0 ? value : value.substring(0, parameters)).trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }

    private void registerRollbackCleanup(UUID companyId, UUID shipmentId, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    safeDelete(companyId, shipmentId, storageKey);
                }
            }
        });
    }

    private void scheduleDeleteAfterCommit(UUID companyId, UUID shipmentId, String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeDelete(companyId, shipmentId, storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeDelete(companyId, shipmentId, storageKey);
            }
        });
    }

    private void safeDelete(UUID companyId, UUID shipmentId, String storageKey) {
        try {
            storage.delete(companyId, shipmentId, storageKey);
        } catch (RuntimeException exception) {
            LOGGER.error("No se pudo limpiar el objeto de documento {}", storageKey, exception);
        }
    }

    private record ValidatedContent(byte[] content, String mediaType, String sha256) {
    }

    public record ShipmentDocumentDownload(ShipmentDocument document, byte[] content) {
    }
}
