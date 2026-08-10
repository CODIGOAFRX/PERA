package com.peraerp.operations.logistics;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.freight.FreightCalculationMethod;
import com.peraerp.operations.freight.FreightDtos.ApplyShipmentFreightRequest;
import com.peraerp.operations.freight.FreightDtos.FreightQuoteResponse;
import com.peraerp.operations.freight.FreightDtos.FreightSimulationRequest;
import com.peraerp.operations.freight.FreightRateService;
import com.peraerp.operations.logistics.LogisticsDtos.ShipmentLineRequest;
import com.peraerp.operations.logistics.LogisticsDtos.ShipmentRequest;
import com.peraerp.operations.logistics.LogisticsDtos.ShipmentResponse;
import com.peraerp.operations.logistics.LogisticsDtos.TransitionTimeRequest;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock ShipmentRepository repository;
    @Mock ShipmentLineRepository lineRepository;
    @Mock ShipmentDocumentRepository documentRepository;
    @Mock LogisticsReferenceValidator references;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock FreightRateService freightRateService;
    @Mock ShipmentDocumentService shipmentDocumentService;

    private ShipmentService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new ShipmentService(repository, lineRepository, documentRepository, references, companyProvider,
                freightRateService, shipmentDocumentService);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsOrderedSnapshotLinesWithoutCallingAnotherService() {
        when(repository.existsByCompanyIdAndShipmentNumberIgnoreCase(companyId, "SHP/2026/1")).thenReturn(false);
        when(references.resolve(companyId, null, null, null))
                .thenReturn(new LogisticsReferenceValidator.Assignment(null, null, null));
        when(repository.saveAndFlush(any(Shipment.class))).thenAnswer(invocation -> {
            Shipment shipment = invocation.getArgument(0);
            ReflectionTestUtils.setField(shipment, "id", UUID.randomUUID());
            return shipment;
        });
        when(lineRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        ShipmentRequest request = request(List.of(line(2, "PROD-2"), line(1, "PROD-1")));

        ShipmentResponse response = service.create(request);

        assertThat(response.shipmentNumber()).isEqualTo("SHP/2026/1");
        assertThat(response.status()).isEqualTo(ShipmentStatus.PLANNED);
        assertThat(response.lines()).extracting(LogisticsDtos.ShipmentLineResponse::sequence)
                .containsExactly(1, 2);
        assertThat(response.lines()).extracting(LogisticsDtos.ShipmentLineResponse::productCodeSnapshot)
                .containsExactly("PROD-1", "PROD-2");
    }

    @Test
    void validatesTheFullPhysicalTransitionSequenceAndTimestamps() {
        Shipment shipment = shipment();
        stubShipment(shipment);
        when(lineRepository.countByCompanyIdAndShipmentId(companyId, shipment.getId())).thenReturn(1L);
        Instant departure = Instant.parse("2026-08-11T08:00:00Z");
        Instant arrival = Instant.parse("2026-08-11T12:00:00Z");
        Instant delivery = Instant.parse("2026-08-11T12:30:00Z");

        service.startPacking(shipment.getId());
        service.markReady(shipment.getId());
        service.dispatch(shipment.getId(), new TransitionTimeRequest(departure));
        service.markInTransit(shipment.getId());
        service.arrive(shipment.getId(), new TransitionTimeRequest(arrival));
        ShipmentResponse result = service.deliver(shipment.getId(), new TransitionTimeRequest(delivery));

        assertThat(result.status()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.actualDepartureAt()).isEqualTo(departure);
        assertThat(result.actualArrivalAt()).isEqualTo(arrival);
        assertThat(result.deliveredAt()).isEqualTo(delivery);
    }

    @Test
    void rejectsOutOfOrderTransitionAndArrivalBeforeDeparture() {
        Shipment planned = shipment();
        when(repository.findByIdAndCompanyId(planned.getId(), companyId)).thenReturn(Optional.of(planned));
        assertThatThrownBy(() -> service.dispatch(planned.getId(),
                new TransitionTimeRequest(Instant.parse("2026-08-11T08:00:00Z"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("PLANNED");

        Shipment dispatched = shipment();
        ReflectionTestUtils.setField(dispatched, "id", UUID.randomUUID());
        dispatched.startPacking();
        dispatched.markReady();
        dispatched.dispatch(Instant.parse("2026-08-11T10:00:00Z"));
        when(repository.findByIdAndCompanyId(dispatched.getId(), companyId)).thenReturn(Optional.of(dispatched));
        assertThatThrownBy(() -> service.arrive(dispatched.getId(),
                new TransitionTimeRequest(Instant.parse("2026-08-11T09:00:00Z"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    void exceptionCanBeResolvedBackToThePreviousOperationalState() {
        Shipment shipment = shipment();
        stubShipment(shipment);
        shipment.startPacking();
        shipment.markReady();
        shipment.dispatch(Instant.parse("2026-08-11T08:00:00Z"));
        shipment.markInTransit();

        service.reportException(shipment.getId(), new LogisticsDtos.StatusNoteRequest("Incidencia de tráfico"));
        ShipmentResponse resolved = service.resolveException(shipment.getId());

        assertThat(resolved.status()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(resolved.statusNote()).isNull();
    }

    @Test
    void crossTenantShipmentLookupNeverFallsBackToFindById() {
        UUID foreignId = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(foreignId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(foreignId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository).findByIdAndCompanyId(foreignId, companyId);
        verify(repository, never()).findById(foreignId);
    }

    @Test
    void deletingAPlannedShipmentDelegatesDocumentObjectCleanup() {
        Shipment shipment = shipment();
        when(repository.findByIdAndCompanyId(shipment.getId(), companyId)).thenReturn(Optional.of(shipment));

        service.delete(shipment.getId());

        verify(shipmentDocumentService).deleteAllForShipment(companyId, shipment.getId());
        verify(repository).delete(shipment);
    }

    @Test
    void applyingFreightUsesTheRouteDistanceWhenNotProvidedExplicitly() {
        UUID routeId = UUID.randomUUID();
        Shipment shipment = shipment();
        shipment.updatePlan("Warehouse", "Customer", null, null, routeId, null, null,
                BigDecimal.ZERO, "EUR", null, null);
        stubShipment(shipment);
        DeliveryRoute route = new DeliveryRoute(companyId, "R-1", "Route", "Warehouse", "Customer");
        ReflectionTestUtils.setField(route, "id", routeId);
        route.update("Route", "Warehouse", "Customer", new BigDecimal("42.000"), 60,
                null, null, null, null, null, null, true);
        when(references.requireRoute(companyId, routeId, false)).thenReturn(route);
        LocalDate pricingDate = LocalDate.of(2026, 8, 10);
        when(freightRateService.resolve(eq(companyId), any(FreightSimulationRequest.class)))
                .thenReturn(new FreightQuoteResponse(UUID.randomUUID(), "KM", "Kilometres",
                        FreightCalculationMethod.PER_KM, "EUR", pricingDate, routeId, null,
                        null, null, new BigDecimal("42.000"), BigDecimal.ZERO,
                        new BigDecimal("21.0000"), new BigDecimal("21.0000"), false, false, 1));

        service.resolveFreight(shipment.getId(), new ApplyShipmentFreightRequest(pricingDate, null));

        ArgumentCaptor<FreightSimulationRequest> request = ArgumentCaptor.forClass(FreightSimulationRequest.class);
        verify(freightRateService).resolve(eq(companyId), request.capture());
        assertThat(request.getValue().distanceKm()).isEqualByComparingTo("42.000");
        assertThat(shipment.getFreightDistanceKmSnapshot()).isEqualByComparingTo("42.000");
    }

    private Shipment shipment() {
        Shipment shipment = new Shipment(companyId, "SHP/2026/1", "EUR");
        ReflectionTestUtils.setField(shipment, "id", UUID.randomUUID());
        shipment.updatePlan("Almacén", "Cliente", null, null, null, null, null,
                BigDecimal.ZERO, "EUR", null, null);
        return shipment;
    }

    private void stubShipment(Shipment shipment) {
        when(repository.findByIdAndCompanyId(shipment.getId(), companyId)).thenReturn(Optional.of(shipment));
        when(lineRepository.findAllByCompanyIdAndShipmentIdOrderByLineSequence(companyId, shipment.getId()))
                .thenReturn(List.of());
        when(documentRepository.findAllByCompanyIdAndShipmentIdOrderByCreatedAtAsc(companyId, shipment.getId()))
                .thenReturn(List.of());
    }

    private ShipmentRequest request(List<ShipmentLineRequest> lines) {
        return new ShipmentRequest("shp/2026/1", "Almacén", "Cliente", null, null, null,
                Instant.parse("2026-08-11T08:00:00Z"), Instant.parse("2026-08-11T12:00:00Z"),
                new BigDecimal("12.5000"), "eur", new BigDecimal("150.000"),
                new BigDecimal("2.500000"), lines);
    }

    private ShipmentLineRequest line(int sequence, String code) {
        return new ShipmentLineRequest(sequence, UUID.randomUUID(), code, "Producto " + code,
                BigDecimal.ONE, "unit", UUID.randomUUID(), "delivery_note", "DN-1");
    }

}
