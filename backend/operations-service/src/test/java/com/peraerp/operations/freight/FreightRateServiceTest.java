package com.peraerp.operations.freight;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.logistics.DeliveryRoute;
import com.peraerp.operations.logistics.LogisticsReferenceValidator;
import com.peraerp.operations.logistics.Shipment;
import com.peraerp.operations.logistics.ShipmentRepository;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.peraerp.operations.freight.FreightDtos.FreightRateRequest;
import static com.peraerp.operations.freight.FreightDtos.FreightSimulationRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreightRateServiceTest {

    @Mock FreightRateRepository repository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock LogisticsReferenceValidator references;
    @Mock CurrentCompanyProvider companyProvider;

    private FreightRateService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new FreightRateService(repository, shipmentRepository, references, companyProvider);
        companyId = UUID.randomUUID();
    }

    @Test
    void resolutionIsDeterministicByPriorityThenScopeAndCalculatesCombination() {
        UUID routeId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        FreightRate generic = rate("GENERIC", 50, null, null, FreightCalculationMethod.FIXED,
                new BigDecimal("99.0000"), null);
        FreightRate carrier = rate("CARRIER", 50, null, carrierId,
                FreightCalculationMethod.FIXED_PLUS_PER_KG, new BigDecimal("10.0000"),
                new BigDecimal("2.000000"));
        FreightRate route = rate("ROUTE", 40, routeId, carrierId, FreightCalculationMethod.FIXED,
                new BigDecimal("1.0000"), null);
        when(repository.findCandidates(companyId, "EUR", LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(route, generic, carrier));

        var quote = service.resolve(companyId, new FreightSimulationRequest(LocalDate.of(2026, 8, 10),
                routeId, carrierId, "eur", new BigDecimal("3.000"), null, null));

        assertThat(quote.rateCode()).isEqualTo("CARRIER");
        assertThat(quote.fixedComponent()).isEqualByComparingTo("10.0000");
        assertThat(quote.variableComponent()).isEqualByComparingTo("6.0000");
        assertThat(quote.amount()).isEqualByComparingTo("16.0000");
        assertThat(quote.eligibleRateCount()).isEqualTo(3);
    }

    @Test
    void routeScopeWinsWhenPriorityIsEqualAndBoundsAreInclusive() {
        UUID routeId = UUID.randomUUID();
        FreightRate generic = rate("A-GENERIC", 10, null, null, FreightCalculationMethod.PER_KM,
                null, BigDecimal.ONE);
        FreightRate scoped = rate("Z-ROUTE", 10, routeId, null, FreightCalculationMethod.PER_KM,
                null, new BigDecimal("2.000000"));
        ReflectionTestUtils.setField(scoped, "minimumDistanceKm", new BigDecimal("5.000"));
        ReflectionTestUtils.setField(scoped, "maximumDistanceKm", new BigDecimal("10.000"));
        when(repository.findCandidates(companyId, "EUR", LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(generic, scoped));

        var quote = service.resolve(companyId, new FreightSimulationRequest(LocalDate.of(2026, 8, 10),
                routeId, null, "EUR", null, null, new BigDecimal("10.000")));

        assertThat(quote.rateCode()).isEqualTo("Z-ROUTE");
        assertThat(quote.amount()).isEqualByComparingTo("20.0000");
    }

    @Test
    void clampsMinimumAndRejectsMissingMetric() {
        FreightRate rate = rate("KG", 0, null, null, FreightCalculationMethod.PER_KG,
                null, new BigDecimal("0.500000"));
        ReflectionTestUtils.setField(rate, "minimumCharge", new BigDecimal("8.0000"));
        when(repository.findCandidates(companyId, "EUR", LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(rate));

        var quote = service.resolve(companyId, new FreightSimulationRequest(LocalDate.of(2026, 8, 10),
                null, null, "EUR", new BigDecimal("2.000"), null, null));
        assertThat(quote.amount()).isEqualByComparingTo("8.0000");
        assertThat(quote.minimumApplied()).isTrue();

        assertThatThrownBy(() -> service.resolve(companyId, new FreightSimulationRequest(
                LocalDate.of(2026, 8, 10), null, null, "EUR", null, null, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("aplicable");
    }

    @Test
    void tenantLookupNeverFallsBackToUnscopedRepositoryMethod() {
        UUID rateId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(repository.findByIdAndCompanyId(rateId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(rateId)).isInstanceOf(ResourceNotFoundException.class);

        verify(repository).findByIdAndCompanyId(rateId, companyId);
        verify(repository, never()).findById(rateId);
    }

    @Test
    void requestRequiresAmountsThatExactlyMatchTheCalculationMethod() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        FreightRateRequest request = request("RATE", FreightCalculationMethod.FIXED_PLUS_PER_M3,
                null, new BigDecimal("2.000000"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("importe fijo");
    }

    @Test
    void shipmentKeepsACompleteRateSnapshotIndependentFromLaterRateChanges() {
        FreightRate rate = rate("SNAP", 1, null, null, FreightCalculationMethod.FIXED,
                new BigDecimal("12.0000"), null);
        when(repository.findCandidates(companyId, "EUR", LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(rate));
        var quote = service.resolve(companyId, new FreightSimulationRequest(LocalDate.of(2026, 8, 10),
                null, null, "EUR", null, null, null));
        Shipment shipment = new Shipment(companyId, "SHP-1", "EUR");
        shipment.updatePlan(null, null, null, null, null, null, null, BigDecimal.ZERO,
                "EUR", null, null);

        shipment.applyFreightQuote(quote.freightRateId(), quote.rateCode(), quote.rateName(),
                quote.calculationMethod(), quote.pricingDate(), quote.fixedComponent(), quote.variableComponent(),
                quote.distanceKm(), quote.minimumApplied(), quote.maximumApplied(), quote.amount(),
                quote.currencyCode());
        rate.update("Nombre cambiado", null, null, "EUR", LocalDate.of(2026, 1, 1), null,
                true, 1, FreightCalculationMethod.FIXED, new BigDecimal("99.0000"), null,
                null, null, null, null, null, null, null, null);

        assertThat(shipment.getFreightRateCodeSnapshot()).isEqualTo("SNAP");
        assertThat(shipment.getFreightRateNameSnapshot()).isEqualTo("SNAP");
        assertThat(shipment.getFreightCost()).isEqualByComparingTo("12.0000");
    }

    @Test
    void simulationUsesConfiguredRouteDistanceWhenTheCallerOmitsIt() {
        UUID routeId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(references.resolve(companyId, null, null, routeId))
                .thenReturn(new LogisticsReferenceValidator.Assignment(null, null, routeId));
        DeliveryRoute route = new DeliveryRoute(companyId, "R-1", "Route", "Origin", "Destination");
        ReflectionTestUtils.setField(route, "id", routeId);
        route.update("Route", "Origin", "Destination", new BigDecimal("12.500"), 30,
                null, null, null, null, null, null, true);
        when(references.requireRoute(companyId, routeId, true)).thenReturn(route);
        FreightRate rate = rate("KM", 1, routeId, null, FreightCalculationMethod.PER_KM,
                null, new BigDecimal("2.000000"));
        when(repository.findCandidates(companyId, "EUR", LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(rate));

        var quote = service.simulate(new FreightSimulationRequest(LocalDate.of(2026, 8, 10),
                routeId, null, "EUR", null, null, null));

        assertThat(quote.distanceKm()).isEqualByComparingTo("12.500");
        assertThat(quote.amount()).isEqualByComparingTo("25.0000");
    }

    private FreightRate rate(String code, int priority, UUID routeId, UUID carrierId,
                             FreightCalculationMethod method, BigDecimal fixed, BigDecimal unit) {
        FreightRate rate = new FreightRate(companyId, code, code, "EUR",
                LocalDate.of(2026, 1, 1), method);
        rate.update(code, routeId, carrierId, "EUR", LocalDate.of(2026, 1, 1), null,
                true, priority, method, fixed, unit, null, null,
                null, null, null, null, null, null);
        ReflectionTestUtils.setField(rate, "id", UUID.randomUUID());
        return rate;
    }

    private FreightRateRequest request(String code, FreightCalculationMethod method,
                                       BigDecimal fixed, BigDecimal unit) {
        return new FreightRateRequest(code, code, null, null, "EUR", LocalDate.of(2026, 1, 1),
                null, true, 0, method, fixed, unit, null, null,
                null, null, null, null, null, null);
    }
}
