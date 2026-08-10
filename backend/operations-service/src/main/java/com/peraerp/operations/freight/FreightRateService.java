package com.peraerp.operations.freight;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.logistics.DeliveryRoute;
import com.peraerp.operations.logistics.LogisticsReferenceValidator;
import com.peraerp.operations.logistics.ShipmentRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.peraerp.operations.freight.FreightDtos.FreightQuoteResponse;
import static com.peraerp.operations.freight.FreightDtos.FreightRateRequest;
import static com.peraerp.operations.freight.FreightDtos.FreightRateResponse;
import static com.peraerp.operations.freight.FreightDtos.FreightSimulationRequest;

@Service
public class FreightRateService {

    private static final int MONEY_SCALE = 4;

    private final FreightRateRepository repository;
    private final ShipmentRepository shipmentRepository;
    private final LogisticsReferenceValidator references;
    private final CurrentCompanyProvider companyProvider;

    public FreightRateService(FreightRateRepository repository, ShipmentRepository shipmentRepository,
                              LogisticsReferenceValidator references, CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.shipmentRepository = shipmentRepository;
        this.references = references;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public FreightRateResponse create(FreightRateRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe una tarifa de flete con el código " + code + ".");
        }
        validateReferences(companyId, request.routeId(), request.carrierId());
        validateRequest(request);
        FreightRate rate = new FreightRate(companyId, code, request.name().trim(),
                normalizeCurrency(request.currencyCode()), request.validFrom(), request.calculationMethod());
        apply(rate, request);
        return FreightRateResponse.from(repository.save(rate));
    }

    @Transactional(readOnly = true)
    public Page<FreightRateResponse> search(Boolean active, FreightCalculationMethod method, UUID routeId,
                                            UUID carrierId, LocalDate validOn, String query, Pageable pageable) {
        UUID companyId = companyProvider.requireCompanyId();
        String normalizedQuery = normalizeNullable(query);
        return repository.search(companyId, active != null, Boolean.TRUE.equals(active), method != null, method,
                        routeId != null, routeId, carrierId != null, carrierId, validOn != null, validOn,
                        normalizedQuery != null, normalizedQuery == null ? "" : normalizedQuery, pageable)
                .map(FreightRateResponse::from);
    }

    @Transactional(readOnly = true)
    public FreightRateResponse findById(UUID id) {
        return FreightRateResponse.from(requireRate(id, companyProvider.requireCompanyId()));
    }

    @Transactional
    public FreightRateResponse update(UUID id, FreightRateRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        FreightRate rate = requireRate(id, companyId);
        if (!rate.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código de una tarifa de flete no se puede modificar.");
        }
        validateReferences(companyId, request.routeId(), request.carrierId());
        validateRequest(request);
        apply(rate, request);
        return FreightRateResponse.from(rate);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        FreightRate rate = requireRate(id, companyId);
        if (shipmentRepository.existsByCompanyIdAndFreightRateId(companyId, id)) {
            throw new BusinessRuleException(
                    "La tarifa está asociada a expediciones. Desactívala para conservar sus snapshots.");
        }
        repository.delete(rate);
    }

    @Transactional(readOnly = true)
    public FreightQuoteResponse simulate(FreightSimulationRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        LogisticsReferenceValidator.Assignment assignment = references.resolve(
                companyId, request.carrierId(), null, request.routeId());
        BigDecimal distanceKm = request.distanceKm();
        if (distanceKm == null && assignment.routeId() != null) {
            distanceKm = references.requireRoute(companyId, assignment.routeId(), true).getDistanceKm();
        }
        FreightSimulationRequest normalized = new FreightSimulationRequest(request.pricingDate(),
                assignment.routeId(), assignment.carrierId(), normalizeCurrency(request.currencyCode()),
                request.weightKg(), request.volumeM3(), distanceKm);
        return resolve(companyId, normalized);
    }

    @Transactional(readOnly = true)
    public FreightQuoteResponse resolve(UUID companyId, FreightSimulationRequest request) {
        String currencyCode = normalizeCurrency(request.currencyCode());
        List<FreightRate> eligible = repository.findCandidates(companyId, currencyCode, request.pricingDate()).stream()
                .filter(rate -> scopeMatches(rate, request.routeId(), request.carrierId()))
                .filter(rate -> rangesMatch(rate, request))
                .filter(rate -> requiredMetric(rate, request) != null)
                .sorted(Comparator.comparingInt(FreightRate::getPriority).reversed()
                        .thenComparing(Comparator.comparingInt(FreightRateService::specificity).reversed())
                        .thenComparing(FreightRate::getCode)
                        .thenComparing(FreightRate::getId))
                .toList();
        if (eligible.isEmpty()) {
            throw new BusinessRuleException("No existe una tarifa de flete aplicable a los datos indicados.");
        }
        FreightRate chosen = eligible.getFirst();
        BigDecimal metric = requiredMetric(chosen, request);
        BigDecimal fixed = chosen.getCalculationMethod().requiresFixedAmount()
                ? chosen.getFixedAmount() : BigDecimal.ZERO;
        BigDecimal variable = chosen.getCalculationMethod().requiresUnitAmount()
                ? chosen.getUnitAmount().multiply(metric) : BigDecimal.ZERO;
        BigDecimal raw = fixed.add(variable).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        boolean minimumApplied = chosen.getMinimumCharge() != null
                && raw.compareTo(chosen.getMinimumCharge()) < 0;
        BigDecimal afterMinimum = minimumApplied ? chosen.getMinimumCharge() : raw;
        boolean maximumApplied = chosen.getMaximumCharge() != null
                && afterMinimum.compareTo(chosen.getMaximumCharge()) > 0;
        BigDecimal amount = (maximumApplied ? chosen.getMaximumCharge() : afterMinimum)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new FreightQuoteResponse(chosen.getId(), chosen.getCode(), chosen.getName(),
                chosen.getCalculationMethod(), chosen.getCurrencyCode(), request.pricingDate(), chosen.getRouteId(),
                chosen.getCarrierId(), request.weightKg(), request.volumeM3(), request.distanceKm(),
                fixed.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                variable.setScale(MONEY_SCALE, RoundingMode.HALF_UP), amount,
                minimumApplied, maximumApplied, eligible.size());
    }

    private void apply(FreightRate rate, FreightRateRequest request) {
        rate.update(request.name().trim(), request.routeId(), request.carrierId(),
                normalizeCurrency(request.currencyCode()), request.validFrom(), request.validTo(),
                request.active() == null || request.active(), request.priority() == null ? 0 : request.priority(),
                request.calculationMethod(), request.fixedAmount(), request.unitAmount(), request.minimumCharge(),
                request.maximumCharge(), request.minimumWeightKg(), request.maximumWeightKg(),
                request.minimumVolumeM3(), request.maximumVolumeM3(), request.minimumDistanceKm(),
                request.maximumDistanceKm());
    }

    private void validateReferences(UUID companyId, UUID routeId, UUID carrierId) {
        DeliveryRoute route = routeId == null ? null : references.requireRoute(companyId, routeId, false);
        if (carrierId != null) {
            references.requireCarrier(companyId, carrierId, false);
        }
        if (route != null && route.getCarrierId() != null && carrierId != null
                && !route.getCarrierId().equals(carrierId)) {
            throw new BusinessRuleException("La ruta y el transportista de la tarifa no son compatibles.");
        }
    }

    private void validateRequest(FreightRateRequest request) {
        if (request.validTo() != null && request.validTo().isBefore(request.validFrom())) {
            throw new BusinessRuleException("La fecha final de vigencia no puede ser anterior a la inicial.");
        }
        FreightCalculationMethod method = request.calculationMethod();
        if (method.requiresFixedAmount() != (request.fixedAmount() != null)) {
            throw new BusinessRuleException(method.requiresFixedAmount()
                    ? "El método seleccionado requiere un importe fijo."
                    : "El método seleccionado no admite un importe fijo.");
        }
        if (method.requiresUnitAmount() != (request.unitAmount() != null)) {
            throw new BusinessRuleException(method.requiresUnitAmount()
                    ? "El método seleccionado requiere un importe por unidad."
                    : "El método seleccionado no admite un importe por unidad.");
        }
        validateRange("peso", request.minimumWeightKg(), request.maximumWeightKg());
        validateRange("volumen", request.minimumVolumeM3(), request.maximumVolumeM3());
        validateRange("distancia", request.minimumDistanceKm(), request.maximumDistanceKm());
        validateRange("cargo", request.minimumCharge(), request.maximumCharge());
    }

    private void validateRange(String name, BigDecimal minimum, BigDecimal maximum) {
        if (minimum != null && maximum != null && maximum.compareTo(minimum) < 0) {
            throw new BusinessRuleException("El máximo de " + name + " no puede ser inferior al mínimo.");
        }
    }

    private boolean scopeMatches(FreightRate rate, UUID routeId, UUID carrierId) {
        return (rate.getRouteId() == null || rate.getRouteId().equals(routeId))
                && (rate.getCarrierId() == null || rate.getCarrierId().equals(carrierId));
    }

    private boolean rangesMatch(FreightRate rate, FreightSimulationRequest request) {
        return inRange(request.weightKg(), rate.getMinimumWeightKg(), rate.getMaximumWeightKg())
                && inRange(request.volumeM3(), rate.getMinimumVolumeM3(), rate.getMaximumVolumeM3())
                && inRange(request.distanceKm(), rate.getMinimumDistanceKm(), rate.getMaximumDistanceKm());
    }

    private boolean inRange(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (minimum == null && maximum == null) {
            return true;
        }
        return value != null && (minimum == null || value.compareTo(minimum) >= 0)
                && (maximum == null || value.compareTo(maximum) <= 0);
    }

    private BigDecimal requiredMetric(FreightRate rate, FreightSimulationRequest request) {
        return switch (rate.getCalculationMethod().metric()) {
            case NONE -> BigDecimal.ZERO;
            case WEIGHT_KG -> request.weightKg();
            case VOLUME_M3 -> request.volumeM3();
            case DISTANCE_KM -> request.distanceKm();
        };
    }

    private static int specificity(FreightRate rate) {
        return (rate.getRouteId() == null ? 0 : 2) + (rate.getCarrierId() == null ? 0 : 1);
    }

    private FreightRate requireRate(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarifa de flete", id));
    }

    private String normalizeCode(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeCurrency(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private String normalizeNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
