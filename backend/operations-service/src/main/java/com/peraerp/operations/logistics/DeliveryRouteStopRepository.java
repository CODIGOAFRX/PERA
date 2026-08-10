package com.peraerp.operations.logistics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliveryRouteStopRepository extends JpaRepository<DeliveryRouteStop, UUID> {
    List<DeliveryRouteStop> findAllByCompanyIdAndRouteIdOrderByStopSequence(UUID companyId, UUID routeId);
    void deleteAllByCompanyIdAndRouteId(UUID companyId, UUID routeId);
}
