package com.peraerp.operations.logistics.storage;

import java.util.UUID;

public interface ShipmentDocumentStorage {

    StoredObject store(UUID companyId, UUID shipmentId, byte[] content);

    byte[] read(UUID companyId, UUID shipmentId, String storageKey, long maximumBytes);

    boolean exists(UUID companyId, UUID shipmentId, String storageKey);

    void delete(UUID companyId, UUID shipmentId, String storageKey);

    record StoredObject(String storageKey, long sizeBytes) {
    }
}
