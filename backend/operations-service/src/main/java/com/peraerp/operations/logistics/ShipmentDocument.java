package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "shipment_documents", uniqueConstraints = @UniqueConstraint(
        name = "uk_shipment_document_storage_key", columnNames = {"company_id", "shipment_id", "storage_key"}))
public class ShipmentDocument extends CompanyScopedEntity {

    @Column(name = "shipment_id", nullable = false, updatable = false)
    private UUID shipmentId;
    @Column(name = "document_type", nullable = false, length = 80)
    private String documentType;
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;
    @Column(name = "media_type", nullable = false, length = 150)
    private String mediaType;
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected ShipmentDocument() {
    }

    public ShipmentDocument(UUID companyId, UUID shipmentId, String documentType, String originalFileName,
                            String storageKey, String mediaType, String sha256, long sizeBytes) {
        super(companyId);
        this.shipmentId = shipmentId;
        this.documentType = documentType;
        this.originalFileName = originalFileName;
        this.storageKey = storageKey;
        this.mediaType = mediaType;
        this.sha256 = sha256;
        this.sizeBytes = sizeBytes;
    }

    public UUID getShipmentId() { return shipmentId; }
    public String getDocumentType() { return documentType; }
    public String getOriginalFileName() { return originalFileName; }
    public String getStorageKey() { return storageKey; }
    public String getMediaType() { return mediaType; }
    public String getSha256() { return sha256; }
    public long getSizeBytes() { return sizeBytes; }
}
