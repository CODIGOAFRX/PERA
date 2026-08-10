package com.peraerp.operations.logistics;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.operations.logistics.LogisticsDtos.DeliveryRouteRequest;
import com.peraerp.operations.logistics.LogisticsDtos.DeliveryRouteResponse;
import com.peraerp.operations.logistics.LogisticsDtos.RouteStopRequest;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryRouteServiceTest {

    @Mock DeliveryRouteRepository repository;
    @Mock DeliveryRouteStopRepository stopRepository;
    @Mock ShipmentRepository shipmentRepository;
    @Mock LogisticsReferenceValidator references;
    @Mock CurrentCompanyProvider companyProvider;

    private DeliveryRouteService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new DeliveryRouteService(repository, stopRepository, shipmentRepository, references, companyProvider);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsStopsInAContinuousConfiguredOrder() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "ROUTE-1")).thenReturn(false);
        when(references.resolve(companyId, null, null, null))
                .thenReturn(new LogisticsReferenceValidator.Assignment(null, null, null));
        when(repository.saveAndFlush(any(DeliveryRoute.class))).thenAnswer(invocation -> {
            DeliveryRoute route = invocation.getArgument(0);
            ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
            return route;
        });
        when(stopRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        DeliveryRouteRequest request = request(List.of(
                stop(2, "Destino intermedio"), stop(1, "Recogida")));

        DeliveryRouteResponse response = service.create(request);

        assertThat(response.code()).isEqualTo("ROUTE-1");
        assertThat(response.distanceKm()).isEqualByComparingTo("42.500");
        assertThat(response.estimatedDurationMinutes()).isEqualTo(75);
        assertThat(response.stops()).extracting(LogisticsDtos.RouteStopResponse::sequence)
                .containsExactly(1, 2);
    }

    @Test
    void rejectsStopGapsAndReversedWindows() {
        DeliveryRouteRequest gap = request(List.of(stop(1, "Uno"), stop(3, "Tres")));
        assertThatThrownBy(() -> service.create(gap))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("continua");

        RouteStopRequest reversed = new RouteStopRequest(1, "Uno", "Madrid",
                Instant.parse("2026-08-11T12:00:00Z"), Instant.parse("2026-08-11T11:00:00Z"), null);
        assertThatThrownBy(() -> service.create(request(List.of(reversed))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    void crossTenantRouteLookupNeverUsesFindById() {
        UUID foreignId = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(foreignId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(foreignId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository).findByIdAndCompanyId(foreignId, companyId);
        verify(repository, never()).findById(foreignId);
    }

    @Test
    void rejectsNonPositiveDistanceAndDurationAtTheServiceBoundary() {
        assertThatThrownBy(() -> service.create(request(List.of(), BigDecimal.ZERO, 10)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("distancia");
        assertThatThrownBy(() -> service.create(request(List.of(), BigDecimal.ONE, 0)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("duración");
    }

    private DeliveryRouteRequest request(List<RouteStopRequest> stops) {
        return request(stops, new BigDecimal("42.500"), 75);
    }

    private DeliveryRouteRequest request(List<RouteStopRequest> stops, BigDecimal distanceKm,
                                         Integer estimatedDurationMinutes) {
        return new DeliveryRouteRequest("route-1", "Ruta centro", "Almacén", "Cliente",
                distanceKm, estimatedDurationMinutes, null, null,
                Instant.parse("2026-08-11T08:00:00Z"), Instant.parse("2026-08-11T16:00:00Z"),
                Instant.parse("2026-08-11T14:00:00Z"), Instant.parse("2026-08-11T17:00:00Z"), true, stops);
    }

    private RouteStopRequest stop(int sequence, String name) {
        return new RouteStopRequest(sequence, name, "Madrid", null, null, null);
    }
}
