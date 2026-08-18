package com.peraerp.operations.logistics;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    Optional<Vehicle> findByIdAndCompanyId(UUID id, UUID companyId);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    boolean existsByCompanyIdAndRegistrationPlateIgnoreCase(UUID companyId, String registrationPlate);
    boolean existsByCompanyIdAndRegistrationPlateIgnoreCaseAndIdNot(UUID companyId, String registrationPlate, UUID id);
    boolean existsByCompanyIdAndCarrierId(UUID companyId, UUID carrierId);

    @Query("select v from Vehicle v where v.companyId = :companyId " +
            "and (:filterActive = false or v.active = :active) " +
            "and (:filterCarrier = false or v.carrierId = :carrierId) " +
            "and (:filterQuery = false or lower(v.code) like lower(concat('%', :query, '%')) " +
            "or lower(coalesce(v.registrationPlate, '')) like lower(concat('%', :query, '%')) " +
            "or lower(v.vehicleType) like lower(concat('%', :query, '%')))")
    Page<Vehicle> search(@Param("companyId") UUID companyId,
                         @Param("filterActive") boolean filterActive,
                         @Param("active") Boolean active,
                         @Param("filterCarrier") boolean filterCarrier,
                         @Param("carrierId") UUID carrierId,
                         @Param("filterQuery") boolean filterQuery,
                         @Param("query") String query,
                         Pageable pageable);
}
