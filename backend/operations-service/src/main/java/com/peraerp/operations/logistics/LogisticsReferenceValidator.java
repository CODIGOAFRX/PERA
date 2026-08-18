package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LogisticsReferenceValidator {

    private final CarrierRepository carrierRepository;
    private final VehicleRepository vehicleRepository;
    private final DeliveryRouteRepository routeRepository;

    public LogisticsReferenceValidator(CarrierRepository carrierRepository, VehicleRepository vehicleRepository,
                                       DeliveryRouteRepository routeRepository) {
        this.carrierRepository = carrierRepository;
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
    }

    public Carrier requireCarrier(UUID companyId, UUID carrierId, boolean requireActive) {
        Carrier carrier = carrierRepository.findByIdAndCompanyId(carrierId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Transportista", carrierId));
        if (requireActive && !carrier.isActive()) {
            throw new BusinessRuleException("No se puede asignar un transportista inactivo.");
        }
        return carrier;
    }

    public Vehicle requireVehicle(UUID companyId, UUID vehicleId, boolean requireActive) {
        Vehicle vehicle = vehicleRepository.findByIdAndCompanyId(vehicleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehículo", vehicleId));
        if (requireActive && !vehicle.isActive()) {
            throw new BusinessRuleException("No se puede asignar un vehículo inactivo.");
        }
        return vehicle;
    }

    public DeliveryRoute requireRoute(UUID companyId, UUID routeId, boolean requireActive) {
        DeliveryRoute route = routeRepository.findByIdAndCompanyId(routeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta de entrega", routeId));
        if (requireActive && !route.isActive()) {
            throw new BusinessRuleException("No se puede asignar una ruta inactiva.");
        }
        return route;
    }

    public Assignment resolve(UUID companyId, UUID carrierId, UUID vehicleId, UUID routeId) {
        DeliveryRoute route = null;
        if (routeId != null) {
            route = requireRoute(companyId, routeId, true);
            if (carrierId != null && route.getCarrierId() != null && !route.getCarrierId().equals(carrierId)) {
                throw new BusinessRuleException("La ruta estÃ¡ vinculada a otro transportista.");
            }
            if (vehicleId != null && route.getVehicleId() != null && !route.getVehicleId().equals(vehicleId)) {
                throw new BusinessRuleException("La ruta estÃ¡ vinculada a otro vehÃ­culo.");
            }
            if (carrierId == null) {
                carrierId = route.getCarrierId();
            }
            if (vehicleId == null) {
                vehicleId = route.getVehicleId();
            }
        }
        Carrier carrier = carrierId == null ? null : requireCarrier(companyId, carrierId, true);
        Vehicle vehicle = vehicleId == null ? null : requireVehicle(companyId, vehicleId, true);
        if (vehicle != null && vehicle.getCarrierId() != null) {
            if (carrier == null) {
                carrierId = vehicle.getCarrierId();
                requireCarrier(companyId, carrierId, true);
            } else if (!vehicle.getCarrierId().equals(carrier.getId())) {
                throw new BusinessRuleException("El vehículo está vinculado a otro transportista.");
            }
        }
        return new Assignment(carrierId, vehicleId, routeId);
    }

    public record Assignment(UUID carrierId, UUID vehicleId, UUID routeId) {
    }
}
