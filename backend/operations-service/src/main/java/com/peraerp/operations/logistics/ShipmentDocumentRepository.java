package com.peraerp.operations.logistics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentDocumentRepository extends JpaRepository<ShipmentDocument, UUID> {
    List<ShipmentDocument> findAllByCompanyIdAndShipmentIdOrderByCreatedAtAsc(UUID companyId, UUID shipmentId);
    Optional<ShipmentDocument> findByIdAndCompanyIdAndShipmentId(UUID id, UUID companyId, UUID shipmentId);
    boolean existsByCompanyIdAndShipmentIdAndStorageKey(UUID companyId, UUID shipmentId, String storageKey);
    void deleteAllByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);
}
