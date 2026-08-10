package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogisticsReferenceValidatorTest {

    @Mock CarrierRepository carrierRepository;
    @Mock VehicleRepository vehicleRepository;
    @Mock DeliveryRouteRepository routeRepository;

    private LogisticsReferenceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LogisticsReferenceValidator(carrierRepository, vehicleRepository, routeRepository);
    }

    @Test
    void crossTenantCarrierIdIsRejectedWithoutAnUnscopedFallback() {
        UUID companyId = UUID.randomUUID();
        UUID foreignCarrierId = UUID.randomUUID();
        when(carrierRepository.findByIdAndCompanyId(foreignCarrierId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.requireCarrier(companyId, foreignCarrierId, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(foreignCarrierId.toString());

        verify(carrierRepository).findByIdAndCompanyId(foreignCarrierId, companyId);
        verify(carrierRepository, never()).findById(foreignCarrierId);
    }

    @Test
    void vehicleCannotBeCombinedWithADifferentCarrier() {
        UUID companyId = UUID.randomUUID();
        Carrier selectedCarrier = carrier(companyId, "SELECTED");
        Carrier vehicleCarrier = carrier(companyId, "VEHICLE_OWNER");
        Vehicle vehicle = new Vehicle(companyId, "TRUCK", "1234-ABC", "TRUCK");
        ReflectionTestUtils.setField(vehicle, "id", UUID.randomUUID());
        vehicle.update("1234-ABC", "TRUCK", vehicleCarrier.getId(), null, null, true);
        when(carrierRepository.findByIdAndCompanyId(selectedCarrier.getId(), companyId))
                .thenReturn(Optional.of(selectedCarrier));
        when(vehicleRepository.findByIdAndCompanyId(vehicle.getId(), companyId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> validator.resolve(companyId, selectedCarrier.getId(), vehicle.getId(), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("otro transportista");
    }

    @Test
    void routeAndVehicleDefaultsAreResolvedInsideTheSameTenant() {
        UUID companyId = UUID.randomUUID();
        Carrier carrier = carrier(companyId, "OWN");
        Vehicle vehicle = new Vehicle(companyId, "VAN", "5678-DEF", "VAN");
        ReflectionTestUtils.setField(vehicle, "id", UUID.randomUUID());
        vehicle.update("5678-DEF", "VAN", carrier.getId(), null, null, true);
        DeliveryRoute route = new DeliveryRoute(companyId, "R-1", "Ruta", "Origen", "Destino");
        ReflectionTestUtils.setField(route, "id", UUID.randomUUID());
        route.update("Ruta", "Origen", "Destino", null, null, carrier.getId(), vehicle.getId(),
                null, null, null, null, true);
        when(routeRepository.findByIdAndCompanyId(route.getId(), companyId)).thenReturn(Optional.of(route));
        when(carrierRepository.findByIdAndCompanyId(carrier.getId(), companyId)).thenReturn(Optional.of(carrier));
        when(vehicleRepository.findByIdAndCompanyId(vehicle.getId(), companyId)).thenReturn(Optional.of(vehicle));

        LogisticsReferenceValidator.Assignment assignment = validator.resolve(companyId, null, null, route.getId());

        assertThat(assignment.carrierId()).isEqualTo(carrier.getId());
        assertThat(assignment.vehicleId()).isEqualTo(vehicle.getId());
        assertThat(assignment.routeId()).isEqualTo(route.getId());
    }

    private Carrier carrier(UUID companyId, String code) {
        Carrier carrier = new Carrier(companyId, code, code, CarrierOwnership.OWN);
        ReflectionTestUtils.setField(carrier, "id", UUID.randomUUID());
        carrier.update(code, CarrierOwnership.OWN, null, null, null, null, null, true);
        return carrier;
    }
}
