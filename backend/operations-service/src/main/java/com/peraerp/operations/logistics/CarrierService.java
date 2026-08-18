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

import static com.peraerp.operations.logistics.LogisticsDtos.CarrierRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.CarrierResponse;

@Service
public class CarrierService {

    private final CarrierRepository repository;
    private final VehicleRepository vehicleRepository;
    private final DeliveryRouteRepository routeRepository;
    private final ShipmentRepository shipmentRepository;
    private final CurrentCompanyProvider companyProvider;

    public CarrierService(CarrierRepository repository, VehicleRepository vehicleRepository,
                          DeliveryRouteRepository routeRepository, ShipmentRepository shipmentRepository,
                          CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
        this.shipmentRepository = shipmentRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public CarrierResponse create(CarrierRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe un transportista con el código " + code + ".");
        }
        Carrier carrier = new Carrier(companyId, code, request.name().trim(), request.ownership());
        apply(carrier, request, true);
        return CarrierResponse.from(repository.save(carrier));
    }

    @Transactional(readOnly = true)
    public Page<CarrierResponse> search(Boolean active, CarrierOwnership ownership, String query, Pageable pageable) {
        String normalizedQuery = normalizeNullable(query);
        return repository.search(companyProvider.requireCompanyId(), active != null, active,
                        ownership != null, ownership, normalizedQuery != null,
                        normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(CarrierResponse::from);
    }

    @Transactional(readOnly = true)
    public CarrierResponse findById(UUID id) {
        return CarrierResponse.from(requireCarrier(id));
    }

    @Transactional
    public CarrierResponse update(UUID id, CarrierRequest request) {
        Carrier carrier = requireCarrier(id);
        if (!carrier.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código de un transportista no se puede modificar.");
        }
        apply(carrier, request, carrier.isActive());
        return CarrierResponse.from(carrier);
    }

    @Transactional
    public void delete(UUID id) {
        Carrier carrier = requireCarrier(id);
        UUID companyId = carrier.getCompanyId();
        if (vehicleRepository.existsByCompanyIdAndCarrierId(companyId, id)
                || routeRepository.existsByCompanyIdAndCarrierId(companyId, id)
                || shipmentRepository.existsByCompanyIdAndCarrierId(companyId, id)) {
            throw new BusinessRuleException("El transportista está en uso y no se puede eliminar; desactívalo.");
        }
        repository.delete(carrier);
    }

    private Carrier requireCarrier(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Transportista", id));
    }

    private void apply(Carrier carrier, CarrierRequest request, boolean defaultActive) {
        carrier.update(request.name().trim(), request.ownership(), normalizeNullable(request.taxIdentifier()),
                normalizeNullable(request.externalIdentifier()), normalizeNullable(request.contactName()),
                lowerNullable(request.contactEmail()), normalizeNullable(request.contactPhone()),
                request.active() == null ? defaultActive : request.active());
    }

    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String lowerNullable(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
