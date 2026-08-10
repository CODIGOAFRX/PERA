package com.peraerp.operations.logistics;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndShipmentNumberIgnoreCase(UUID companyId, String shipmentNumber);
    boolean existsByCompanyIdAndCarrierId(UUID companyId, UUID carrierId);
    boolean existsByCompanyIdAndVehicleId(UUID companyId, UUID vehicleId);
    boolean existsByCompanyIdAndRouteId(UUID companyId, UUID routeId);
    boolean existsByCompanyIdAndFreightRateId(UUID companyId, UUID freightRateId);

    @Query("select s from Shipment s where s.companyId = :companyId " +
            "and (:filterStatus = false or s.status = :status) " +
            "and (:filterCarrier = false or s.carrierId = :carrierId) " +
            "and (:filterVehicle = false or s.vehicleId = :vehicleId) " +
            "and (:filterRoute = false or s.routeId = :routeId) " +
            "and (:filterProduct = false or exists (select l.id from ShipmentLine l " +
            "where l.companyId = :companyId and l.shipmentId = s.id and l.productId = :productId)) " +
            "and (:filterSourceDocument = false or exists (select l.id from ShipmentLine l " +
            "where l.companyId = :companyId and l.shipmentId = s.id and l.sourceDocumentId = :sourceDocumentId)) " +
            "and (:filterPlannedFrom = false or s.plannedDepartureAt >= :plannedFrom) " +
            "and (:filterPlannedTo = false or s.plannedDepartureAt <= :plannedTo) " +
            "and (:filterQuery = false or lower(s.shipmentNumber) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(s.originSnapshot, '')) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(s.destinationSnapshot, '')) like lower(concat('%', :query, '%')) " +
            "or exists (select l.id from ShipmentLine l where l.companyId = :companyId and l.shipmentId = s.id " +
            "and (lower(coalesce(l.productCodeSnapshot, '')) like lower(concat('%', :query, '%')) " +
            "or lower(l.productNameSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(l.sourceDocumentType, '')) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(l.sourceDocumentNumberSnapshot, '')) like lower(concat('%', :query, '%')))))")
    Page<Shipment> search(@Param("companyId") UUID companyId,
                          @Param("filterStatus") boolean filterStatus,
                          @Param("status") ShipmentStatus status,
                          @Param("filterCarrier") boolean filterCarrier,
                          @Param("carrierId") UUID carrierId,
                          @Param("filterVehicle") boolean filterVehicle,
                          @Param("vehicleId") UUID vehicleId,
                          @Param("filterRoute") boolean filterRoute,
                          @Param("routeId") UUID routeId,
                          @Param("filterProduct") boolean filterProduct,
                          @Param("productId") UUID productId,
                          @Param("filterSourceDocument") boolean filterSourceDocument,
                          @Param("sourceDocumentId") UUID sourceDocumentId,
                          @Param("filterPlannedFrom") boolean filterPlannedFrom,
                          @Param("plannedFrom") Instant plannedFrom,
                          @Param("filterPlannedTo") boolean filterPlannedTo,
                          @Param("plannedTo") Instant plannedTo,
                          @Param("filterQuery") boolean filterQuery,
                          @Param("query") String query,
                          Pageable pageable);
}
