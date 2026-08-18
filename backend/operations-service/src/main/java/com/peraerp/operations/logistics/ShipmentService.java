package com.peraerp.operations.logistics;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.freight.FreightDtos.ApplyShipmentFreightRequest;
import com.peraerp.operations.freight.FreightDtos.FreightQuoteResponse;
import com.peraerp.operations.freight.FreightDtos.FreightSimulationRequest;
import com.peraerp.operations.freight.FreightRateService;
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

import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentLineRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.ShipmentResponse;
import static com.peraerp.operations.logistics.LogisticsDtos.StatusNoteRequest;
import static com.peraerp.operations.logistics.LogisticsDtos.TransitionTimeRequest;

@Service
public class ShipmentService {

    private final ShipmentRepository repository;
    private final ShipmentLineRepository lineRepository;
    private final ShipmentDocumentRepository documentRepository;
    private final LogisticsReferenceValidator references;
    private final CurrentCompanyProvider companyProvider;
    private final FreightRateService freightRateService;
    private final ShipmentDocumentService documentService;

    public ShipmentService(ShipmentRepository repository, ShipmentLineRepository lineRepository,
                           ShipmentDocumentRepository documentRepository, LogisticsReferenceValidator references,
                           CurrentCompanyProvider companyProvider, FreightRateService freightRateService,
                           ShipmentDocumentService documentService) {
        this.repository = repository;
        this.lineRepository = lineRepository;
        this.documentRepository = documentRepository;
        this.references = references;
        this.companyProvider = companyProvider;
        this.freightRateService = freightRateService;
        this.documentService = documentService;
    }

    @Transactional
    public ShipmentResponse create(ShipmentRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String number = normalizeNumber(request.shipmentNumber());
        if (repository.existsByCompanyIdAndShipmentNumberIgnoreCase(companyId, number)) {
            throw new BusinessRuleException("Ya existe un envío con el número " + number + ".");
        }
        validatePlanTimes(request.plannedDepartureAt(), request.plannedArrivalAt());
        List<ShipmentLineRequest> lines = normalizeLines(request.lines());
        LogisticsReferenceValidator.Assignment assignment = references.resolve(
                companyId, request.carrierId(), request.vehicleId(), request.routeId());
        Shipment shipment = new Shipment(companyId, number, normalizeCurrency(request.currencyCode()));
        applyPlan(shipment, request, assignment);
        repository.saveAndFlush(shipment);
        List<ShipmentLine> lineEntities = createLines(companyId, shipment.getId(), lines);
        lineRepository.saveAll(lineEntities);
        return ShipmentResponse.from(shipment, lineEntities, List.of());
    }

    @Transactional(readOnly = true)
    public Page<ShipmentResponse> search(ShipmentStatus status, UUID carrierId, UUID vehicleId, UUID routeId,
                                         UUID productId, UUID sourceDocumentId, Instant plannedFrom,
                                         Instant plannedTo, String query, Pageable pageable) {
        if (plannedFrom != null && plannedTo != null && plannedTo.isBefore(plannedFrom)) {
            throw new BusinessRuleException("El final del intervalo de búsqueda no puede ser anterior al inicio.");
        }
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalizeNullable(query);
        return repository.search(companyId, status != null, status, carrierId != null, carrierId,
                        vehicleId != null, vehicleId, routeId != null, routeId, productId != null, productId,
                        sourceDocumentId != null, sourceDocumentId, plannedFrom != null, plannedFrom,
                        plannedTo != null, plannedTo, normalizedQuery != null,
                        normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(shipment -> response(companyId, shipment));
    }

    @Transactional(readOnly = true)
    public ShipmentResponse findById(UUID id) {
        Shipment shipment = requireShipment(id);
        return response(shipment.getCompanyId(), shipment);
    }

    @Transactional
    public ShipmentResponse update(UUID id, ShipmentRequest request) {
        Shipment shipment = requireShipment(id);
        if (!shipment.getShipmentNumber().equalsIgnoreCase(request.shipmentNumber().trim())) {
            throw new BusinessRuleException("El número de un envío no se puede modificar.");
        }
        validatePlanTimes(request.plannedDepartureAt(), request.plannedArrivalAt());
        List<ShipmentLineRequest> lines = normalizeLines(request.lines());
        LogisticsReferenceValidator.Assignment assignment = references.resolve(
                shipment.getCompanyId(), request.carrierId(), request.vehicleId(), request.routeId());
        try {
            applyPlan(shipment, request, assignment);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        lineRepository.deleteAllByCompanyIdAndShipmentId(shipment.getCompanyId(), shipment.getId());
        lineRepository.flush();
        List<ShipmentLine> lineEntities = createLines(shipment.getCompanyId(), shipment.getId(), lines);
        lineRepository.saveAll(lineEntities);
        return ShipmentResponse.from(shipment, lineEntities, documents(shipment));
    }

    @Transactional
    public ShipmentResponse resolveFreight(UUID id, ApplyShipmentFreightRequest request) {
        Shipment shipment = requireShipment(id);
        BigDecimal distanceKm = request.distanceKm();
        if (distanceKm == null && shipment.getRouteId() != null) {
            distanceKm = references.requireRoute(shipment.getCompanyId(), shipment.getRouteId(), false)
                    .getDistanceKm();
        }
        FreightQuoteResponse quote = freightRateService.resolve(shipment.getCompanyId(),
                new FreightSimulationRequest(request.pricingDate(), shipment.getRouteId(), shipment.getCarrierId(),
                        shipment.getCurrencyCode(), shipment.getTotalWeightKg(), shipment.getTotalVolumeM3(),
                        distanceKm));
        try {
            shipment.applyFreightQuote(quote.freightRateId(), quote.rateCode(), quote.rateName(),
                    quote.calculationMethod(), quote.pricingDate(), quote.fixedComponent(),
                    quote.variableComponent(), quote.distanceKm(), quote.minimumApplied(), quote.maximumApplied(),
                    quote.amount(), quote.currencyCode());
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
        return response(shipment.getCompanyId(), shipment);
    }

    @Transactional
    public void delete(UUID id) {
        Shipment shipment = requireShipment(id);
        if (shipment.getStatus() != ShipmentStatus.PLANNED) {
            throw new BusinessRuleException("Solo se puede eliminar un envío en estado PLANNED.");
        }
        UUID companyId = shipment.getCompanyId();
        lineRepository.deleteAllByCompanyIdAndShipmentId(companyId, id);
        documentService.deleteAllForShipment(companyId, id);
        lineRepository.flush();
        repository.delete(shipment);
    }

    @Transactional
    public void deleteDocument(UUID shipmentId, UUID documentId) {
        documentService.delete(shipmentId, documentId);
    }

    @Transactional
    public ShipmentResponse startPacking(UUID id) {
        return transition(id, Shipment::startPacking);
    }

    @Transactional
    public ShipmentResponse markReady(UUID id) {
        Shipment shipment = requireShipment(id);
        if (lineRepository.countByCompanyIdAndShipmentId(shipment.getCompanyId(), id) == 0) {
            throw new BusinessRuleException("Un envío sin líneas no puede marcarse como preparado.");
        }
        applyTransition(shipment, Shipment::markReady);
        return response(shipment.getCompanyId(), shipment);
    }

    @Transactional
    public ShipmentResponse dispatch(UUID id, TransitionTimeRequest request) {
        Instant occurredAt = occurredAt(request);
        Shipment shipment = requireShipment(id);
        applyTransition(shipment, value -> value.dispatch(occurredAt));
        return response(shipment.getCompanyId(), shipment);
    }

    @Transactional
    public ShipmentResponse markInTransit(UUID id) {
        return transition(id, Shipment::markInTransit);
    }

    @Transactional
    public ShipmentResponse arrive(UUID id, TransitionTimeRequest request) {
        Instant occurredAt = occurredAt(request);
        Shipment shipment = requireShipment(id);
        applyTransition(shipment, value -> value.arrive(occurredAt));
        return response(shipment.getCompanyId(), shipment);
    }

    @Transactional
    public ShipmentResponse deliver(UUID id, TransitionTimeRequest request) {
        Instant occurredAt = occurredAt(request);
        Shipment shipment = requireShipment(id);
        applyTransition(shipment, value -> value.deliver(occurredAt));
        return response(shipment.getCompanyId(), shipment);
    }

    @Transactional
    public ShipmentResponse reportException(UUID id, StatusNoteRequest request) {
        Shipment shipment = requireShipment(id);
        applyTransition(shipment, value -> value.reportException(request.reason().trim()));
        return response(shipment.getCompanyId(), shipment);
    }

    @Transactional
    public ShipmentResponse resolveException(UUID id) {
        return transition(id, Shipment::resolveException);
    }

    @Transactional
    public ShipmentResponse cancel(UUID id, StatusNoteRequest request) {
        Shipment shipment = requireShipment(id);
        applyTransition(shipment, value -> value.cancel(request.reason().trim()));
        return response(shipment.getCompanyId(), shipment);
    }

    private ShipmentResponse transition(UUID id, ShipmentMutation mutation) {
        Shipment shipment = requireShipment(id);
        applyTransition(shipment, mutation);
        return response(shipment.getCompanyId(), shipment);
    }

    private void applyTransition(Shipment shipment, ShipmentMutation mutation) {
        try {
            mutation.apply(shipment);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(exception.getMessage());
        }
    }

    private Shipment requireShipment(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Envío", id));
    }

    private ShipmentResponse response(UUID companyId, Shipment shipment) {
        return ShipmentResponse.from(shipment,
                lineRepository.findAllByCompanyIdAndShipmentIdOrderByLineSequence(companyId, shipment.getId()),
                documentRepository.findAllByCompanyIdAndShipmentIdOrderByCreatedAtAsc(companyId, shipment.getId()));
    }

    private List<ShipmentDocument> documents(Shipment shipment) {
        return documentRepository.findAllByCompanyIdAndShipmentIdOrderByCreatedAtAsc(
                shipment.getCompanyId(), shipment.getId());
    }

    private void applyPlan(Shipment shipment, ShipmentRequest request,
                           LogisticsReferenceValidator.Assignment assignment) {
        shipment.updatePlan(normalizeNullable(request.origin()), normalizeNullable(request.destination()),
                assignment.carrierId(), assignment.vehicleId(), assignment.routeId(), request.plannedDepartureAt(),
                request.plannedArrivalAt(), request.freightCost(), normalizeCurrency(request.currencyCode()),
                request.totalWeightKg(), request.totalVolumeM3());
    }

    private List<ShipmentLineRequest> normalizeLines(List<ShipmentLineRequest> requests) {
        List<ShipmentLineRequest> sorted = requests.stream()
                .sorted(Comparator.comparingInt(ShipmentLineRequest::sequence)).toList();
        for (int index = 0; index < sorted.size(); index++) {
            ShipmentLineRequest line = sorted.get(index);
            if (line.sequence() != index + 1) {
                throw new BusinessRuleException("La secuencia de líneas debe ser continua y comenzar en 1.");
            }
            boolean hasDocumentId = line.sourceDocumentId() != null;
            boolean hasDocumentType = line.sourceDocumentType() != null && !line.sourceDocumentType().isBlank();
            boolean hasDocumentNumber = line.sourceDocumentNumberSnapshot() != null
                    && !line.sourceDocumentNumberSnapshot().isBlank();
            if (hasDocumentId != hasDocumentType || hasDocumentId != hasDocumentNumber) {
                throw new BusinessRuleException("La referencia documental de una línea requiere UUID, tipo y número snapshot.");
            }
            if (line.productId() != null
                    && (line.productCodeSnapshot() == null || line.productCodeSnapshot().isBlank())) {
                throw new BusinessRuleException("Una referencia de producto requiere su código snapshot.");
            }
        }
        return sorted;
    }

    private List<ShipmentLine> createLines(UUID companyId, UUID shipmentId, List<ShipmentLineRequest> requests) {
        return requests.stream().map(line -> new ShipmentLine(companyId, shipmentId, line.sequence(), line.productId(),
                normalizeNullable(line.productCodeSnapshot()), line.productNameSnapshot().trim(), line.quantity(),
                normalizeType(line.unitOfMeasureSnapshot()), line.sourceDocumentId(),
                normalizeNullableUpper(line.sourceDocumentType()),
                normalizeNullable(line.sourceDocumentNumberSnapshot()))).toList();
    }

    private void validatePlanTimes(Instant departure, Instant arrival) {
        if (departure != null && arrival != null && arrival.isBefore(departure)) {
            throw new BusinessRuleException("La llegada prevista no puede ser anterior a la salida prevista.");
        }
    }

    private Instant occurredAt(TransitionTimeRequest request) {
        return request == null || request.occurredAt() == null ? Instant.now() : request.occurredAt();
    }

    private String normalizeNumber(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeCurrency(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeType(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeNullableUpper(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    @FunctionalInterface
    private interface ShipmentMutation {
        void apply(Shipment shipment);
    }
}
