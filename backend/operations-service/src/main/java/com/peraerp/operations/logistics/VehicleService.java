package com.peraerp.operations.logistics;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

import static com.peraerp.operations.logistics.LogisticsDtos.VehicleRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.VehicleResponse;

@Service
public class VehicleService {

    private final VehicleRepository repository;
    private final DeliveryRouteRepository routeRepository;
    private final ShipmentRepository shipmentRepository;
    private final LogisticsReferenceValidator references;
    private final CurrentCompanyProvider companyProvider;

    public VehicleService(VehicleRepository repository, DeliveryRouteRepository routeRepository,
                          ShipmentRepository shipmentRepository, LogisticsReferenceValidator references,
                          CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.routeRepository = routeRepository;
        this.shipmentRepository = shipmentRepository;
        this.references = references;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        String plate = normalizePlate(request.registrationPlate());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe un vehículo con el código " + code + ".");
        }
        ensurePlateAvailable(companyId, plate, null);
        if (request.carrierId() != null) {
            references.requireCarrier(companyId, request.carrierId(), true);
        }
        Vehicle vehicle = new Vehicle(companyId, code, plate, request.vehicleType().trim());
        apply(vehicle, request, true, plate);
        return VehicleResponse.from(repository.save(vehicle));
    }

    @Transactional(readOnly = true)
    public Page<VehicleResponse> search(Boolean active, UUID carrierId, String query, Pageable pageable) {
        String normalizedQuery = normalizeNullable(query);
        return repository.search(companyProvider.requireCompanyId(), active != null, active,
                        carrierId != null, carrierId, normalizedQuery != null,
                        normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(VehicleResponse::from);
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(UUID id) {
        return VehicleResponse.from(requireVehicle(id));
    }

    @Transactional
    public VehicleResponse update(UUID id, VehicleRequest request) {
        Vehicle vehicle = requireVehicle(id);
        if (!vehicle.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código de un vehículo no se puede modificar.");
        }
        UUID companyId = vehicle.getCompanyId();
        String plate = normalizePlate(request.registrationPlate());
        ensurePlateAvailable(companyId, plate, id);
        if (request.carrierId() != null) {
            references.requireCarrier(companyId, request.carrierId(), true);
        }
        apply(vehicle, request, vehicle.isActive(), plate);
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public void delete(UUID id) {
        Vehicle vehicle = requireVehicle(id);
        UUID companyId = vehicle.getCompanyId();
        if (routeRepository.existsByCompanyIdAndVehicleId(companyId, id)
                || shipmentRepository.existsByCompanyIdAndVehicleId(companyId, id)) {
            throw new BusinessRuleException("El vehículo está en uso y no se puede eliminar; desactívalo.");
        }
        repository.delete(vehicle);
    }

    private Vehicle requireVehicle(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehículo", id));
    }

    private void ensurePlateAvailable(UUID companyId, String plate, UUID ignoredId) {
        if (plate == null) {
            return;
        }
        boolean exists = ignoredId == null
                ? repository.existsByCompanyIdAndRegistrationPlateIgnoreCase(companyId, plate)
                : repository.existsByCompanyIdAndRegistrationPlateIgnoreCaseAndIdNot(companyId, plate, ignoredId);
        if (exists) {
            throw new BusinessRuleException("Ya existe un vehículo con la matrícula indicada.");
        }
    }

    private void apply(Vehicle vehicle, VehicleRequest request, boolean defaultActive, String plate) {
        vehicle.update(plate, request.vehicleType().trim(), request.carrierId(), request.capacityWeightKg(),
                request.capacityVolumeM3(), request.active() == null ? defaultActive : request.active());
    }

    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizePlate(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
