package com.peraerp.operations.logistics;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryRouteRepository extends JpaRepository<DeliveryRoute, UUID> {
    Optional<DeliveryRoute> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    boolean existsByCompanyIdAndCarrierId(UUID companyId, UUID carrierId);
    boolean existsByCompanyIdAndVehicleId(UUID companyId, UUID vehicleId);

    @Query("select r from DeliveryRoute r where r.companyId = :companyId " +
            "and (:filterActive = false or r.active = :active) " +
            "and (:filterCarrier = false or r.carrierId = :carrierId) " +
            "and (:filterVehicle = false or r.vehicleId = :vehicleId) " +
            "and (:filterQuery = false or lower(r.code) like lower(concat('%', :query, '%')) " +
            "or lower(r.name) like lower(concat('%', :query, '%')) " +
            "or lower(r.originSnapshot) like lower(concat('%', :query, '%')) " +
            "or lower(r.destinationSnapshot) like lower(concat('%', :query, '%')))")
    Page<DeliveryRoute> search(@Param("companyId") UUID companyId,
                               @Param("filterActive") boolean filterActive,
                               @Param("active") Boolean active,
                               @Param("filterCarrier") boolean filterCarrier,
                               @Param("carrierId") UUID carrierId,
                               @Param("filterVehicle") boolean filterVehicle,
                               @Param("vehicleId") UUID vehicleId,
                               @Param("filterQuery") boolean filterQuery,
                               @Param("query") String query,
                               Pageable pageable);
}
