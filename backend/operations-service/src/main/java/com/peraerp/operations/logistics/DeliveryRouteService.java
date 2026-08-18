package com.peraerp.operations.logistics;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.peraerp.operations.logistics.LogisticsDtos.DeliveryRouteRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.DeliveryRouteResponse;
import static com.peraerp.operations.logistics.LogisticsDtos.RouteStopRequest;

@Service
public class DeliveryRouteService {

    private final DeliveryRouteRepository repository;
    private final DeliveryRouteStopRepository stopRepository;
    private final ShipmentRepository shipmentRepository;
    private final LogisticsReferenceValidator references;
    private final CurrentCompanyProvider companyProvider;

    public DeliveryRouteService(DeliveryRouteRepository repository, DeliveryRouteStopRepository stopRepository,
                                ShipmentRepository shipmentRepository, LogisticsReferenceValidator references,
                                CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.stopRepository = stopRepository;
        this.shipmentRepository = shipmentRepository;
        this.references = references;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public DeliveryRouteResponse create(DeliveryRouteRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe una ruta con el código " + code + ".");
        }
        validateTimes(request.plannedDepartureAt(), request.plannedArrivalAt(), "plan de ruta");
        validateTimes(request.deliveryWindowStart(), request.deliveryWindowEnd(), "ventana de entrega");
        validateMetrics(request.distanceKm(), request.estimatedDurationMinutes());
        List<RouteStopRequest> stops = normalizeStops(request.stops());
        LogisticsReferenceValidator.Assignment assignment = references.resolve(
                companyId, request.carrierId(), request.vehicleId(), null);
        DeliveryRoute route = new DeliveryRoute(companyId, code, request.name().trim(), request.origin().trim(),
                request.destination().trim());
        route.update(request.name().trim(), request.origin().trim(), request.destination().trim(),
                request.distanceKm(), request.estimatedDurationMinutes(),
                assignment.carrierId(), assignment.vehicleId(), request.plannedDepartureAt(), request.plannedArrivalAt(),
                request.deliveryWindowStart(), request.deliveryWindowEnd(),
                request.active() == null || request.active());
        repository.saveAndFlush(route);
        List<DeliveryRouteStop> entities = createStops(companyId, route.getId(), stops);
        stopRepository.saveAll(entities);
        return DeliveryRouteResponse.from(route, entities);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryRouteResponse> search(Boolean active, UUID carrierId, UUID vehicleId,
                                              String query, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalizeNullable(query);
        return repository.search(companyId, active != null, active, carrierId != null, carrierId,
                        vehicleId != null, vehicleId, normalizedQuery != null,
                        normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(route -> response(companyId, route));
    }

    @Transactional(readOnly = true)
    public DeliveryRouteResponse findById(UUID id) {
        DeliveryRoute route = requireRoute(id);
        return response(route.getCompanyId(), route);
    }

    @Transactional
    public DeliveryRouteResponse update(UUID id, DeliveryRouteRequest request) {
        DeliveryRoute route = requireRoute(id);
        if (!route.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código de una ruta no se puede modificar.");
        }
        validateTimes(request.plannedDepartureAt(), request.plannedArrivalAt(), "plan de ruta");
        validateTimes(request.deliveryWindowStart(), request.deliveryWindowEnd(), "ventana de entrega");
        validateMetrics(request.distanceKm(), request.estimatedDurationMinutes());
        List<RouteStopRequest> stops = normalizeStops(request.stops());
        LogisticsReferenceValidator.Assignment assignment = references.resolve(
                route.getCompanyId(), request.carrierId(), request.vehicleId(), null);
        route.update(request.name().trim(), request.origin().trim(), request.destination().trim(),
                request.distanceKm(), request.estimatedDurationMinutes(),
                assignment.carrierId(), assignment.vehicleId(), request.plannedDepartureAt(), request.plannedArrivalAt(),
                request.deliveryWindowStart(), request.deliveryWindowEnd(),
                request.active() == null ? route.isActive() : request.active());
        stopRepository.deleteAllByCompanyIdAndRouteId(route.getCompanyId(), route.getId());
        stopRepository.flush();
        List<DeliveryRouteStop> entities = createStops(route.getCompanyId(), route.getId(), stops);
        stopRepository.saveAll(entities);
        return DeliveryRouteResponse.from(route, entities);
    }

    @Transactional
    public void delete(UUID id) {
        DeliveryRoute route = requireRoute(id);
        if (shipmentRepository.existsByCompanyIdAndRouteId(route.getCompanyId(), id)) {
            throw new BusinessRuleException("La ruta está en uso y no se puede eliminar; desactívala.");
        }
        stopRepository.deleteAllByCompanyIdAndRouteId(route.getCompanyId(), id);
        stopRepository.flush();
        repository.delete(route);
    }

    private DeliveryRoute requireRoute(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ruta de entrega", id));
    }

    private DeliveryRouteResponse response(UUID companyId, DeliveryRoute route) {
        return DeliveryRouteResponse.from(route,
                stopRepository.findAllByCompanyIdAndRouteIdOrderByStopSequence(companyId, route.getId()));
    }

    private List<RouteStopRequest> normalizeStops(List<RouteStopRequest> requests) {
        List<RouteStopRequest> sorted = requests.stream()
                .sorted(Comparator.comparingInt(RouteStopRequest::sequence)).toList();
        for (int index = 0; index < sorted.size(); index++) {
            RouteStopRequest stop = sorted.get(index);
            if (stop.sequence() != index + 1) {
                throw new BusinessRuleException("La secuencia de paradas debe ser continua y comenzar en 1.");
            }
            validateTimes(stop.windowStart(), stop.windowEnd(), "ventana de la parada " + stop.sequence());
        }
        return sorted;
    }

    private List<DeliveryRouteStop> createStops(UUID companyId, UUID routeId, List<RouteStopRequest> stops) {
        return stops.stream().map(stop -> new DeliveryRouteStop(companyId, routeId, stop.sequence(), stop.name().trim(),
                stop.location().trim(), stop.windowStart(), stop.windowEnd(), normalizeNullable(stop.instructions())))
                .toList();
    }

    private void validateTimes(Instant start, Instant end, String label) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessRuleException("El final de " + label + " no puede ser anterior al inicio.");
        }
    }

    private void validateMetrics(BigDecimal distanceKm, Integer estimatedDurationMinutes) {
        if (distanceKm != null && distanceKm.signum() <= 0) {
            throw new BusinessRuleException("La distancia de la ruta debe ser mayor que cero.");
        }
        if (estimatedDurationMinutes != null && estimatedDurationMinutes <= 0) {
            throw new BusinessRuleException("La duración estimada debe ser mayor que cero.");
        }
    }

    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
