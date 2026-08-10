package com.peraerp.operations.logistics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipmentLineRepository extends JpaRepository<ShipmentLine, UUID> {
    List<ShipmentLine> findAllByCompanyIdAndShipmentIdOrderByLineSequence(UUID companyId, UUID shipmentId);
    long countByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);
    void deleteAllByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);
}
