package com.peraerp.operations.salesdelivery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesDeliveryLineRepository extends JpaRepository<SalesDeliveryLine, UUID> {

    List<SalesDeliveryLine> findAllByCompanyIdAndDeliveryIdOrderByLineSequenceAsc(UUID companyId, UUID deliveryId);

    void deleteAllByCompanyIdAndDeliveryId(UUID companyId, UUID deliveryId);
}
