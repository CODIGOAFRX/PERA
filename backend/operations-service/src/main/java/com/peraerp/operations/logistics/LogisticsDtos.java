package com.peraerp.operations.logistics;

import com.peraerp.operations.freight.FreightCalculationMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LogisticsDtos {

    private LogisticsDtos() {
    }

    public record CarrierRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,59}$") String code,
            @NotBlank @Size(max = 180) String name,
            @NotNull CarrierOwnership ownership,
            @Size(max = 40) String taxIdentifier,
            @Size(max = 100) String externalIdentifier,
            @Size(max = 180) String contactName,
            @Email @Size(max = 254) String contactEmail,
            @Size(max = 40) String contactPhone,
            Boolean active
    ) {
    }

    public record CarrierResponse(
            UUID id, String code, String name, CarrierOwnership ownership, String taxIdentifier,
            String externalIdentifier, String contactName, String contactEmail, String contactPhone,
            boolean active, Instant createdAt, Instant updatedAt
    ) {
        static CarrierResponse from(Carrier carrier) {
            return new CarrierResponse(carrier.getId(), carrier.getCode(), carrier.getName(), carrier.getOwnership(),
                    carrier.getTaxIdentifier(), carrier.getExternalIdentifier(), carrier.getContactName(),
                    carrier.getContactEmail(), carrier.getContactPhone(), carrier.isActive(), carrier.getCreatedAt(),
                    carrier.getUpdatedAt());
        }
    }

    public record VehicleRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,59}$") String code,
            @Size(max = 30) String registrationPlate,
            @NotBlank @Size(max = 80) String vehicleType,
            UUID carrierId,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal capacityWeightKg,
            @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal capacityVolumeM3,
            Boolean active
    ) {
    }

    public record VehicleResponse(
            UUID id, String code, String registrationPlate, String vehicleType, UUID carrierId,
            BigDecimal capacityWeightKg, BigDecimal capacityVolumeM3, boolean active,
            Instant createdAt, Instant updatedAt
    ) {
        static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(vehicle.getId(), vehicle.getCode(), vehicle.getRegistrationPlate(),
                    vehicle.getVehicleType(), vehicle.getCarrierId(), vehicle.getCapacityWeightKg(),
                    vehicle.getCapacityVolumeM3(), vehicle.isActive(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
        }
    }

    public record RouteStopRequest(
            @Positive int sequence,
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 500) String location,
            Instant windowStart,
            Instant windowEnd,
            @Size(max = 1000) String instructions
    ) {
    }

    public record DeliveryRouteRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,59}$") String code,
            @NotBlank @Size(max = 180) String name,
            @NotBlank @Size(max = 500) String origin,
            @NotBlank @Size(max = 500) String destination,
            @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 3) BigDecimal distanceKm,
            @Positive Integer estimatedDurationMinutes,
            UUID carrierId,
            UUID vehicleId,
            Instant plannedDepartureAt,
            Instant plannedArrivalAt,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            Boolean active,
            @NotNull @Size(max = 200) List<@Valid RouteStopRequest> stops
    ) {
    }

    public record RouteStopResponse(
            UUID id, int sequence, String name, String location, Instant windowStart,
            Instant windowEnd, String instructions
    ) {
        static RouteStopResponse from(DeliveryRouteStop stop) {
            return new RouteStopResponse(stop.getId(), stop.getStopSequence(), stop.getName(),
                    stop.getLocationSnapshot(), stop.getWindowStart(), stop.getWindowEnd(), stop.getInstructions());
        }
    }

    public record DeliveryRouteResponse(
            UUID id, String code, String name, String origin, String destination,
            BigDecimal distanceKm, Integer estimatedDurationMinutes, UUID carrierId, UUID vehicleId,
            Instant plannedDepartureAt, Instant plannedArrivalAt, Instant deliveryWindowStart,
            Instant deliveryWindowEnd, boolean active, List<RouteStopResponse> stops,
            Instant createdAt, Instant updatedAt
    ) {
        static DeliveryRouteResponse from(DeliveryRoute route, List<DeliveryRouteStop> stops) {
            return new DeliveryRouteResponse(route.getId(), route.getCode(), route.getName(), route.getOriginSnapshot(),
                    route.getDestinationSnapshot(), route.getDistanceKm(), route.getEstimatedDurationMinutes(),
                    route.getCarrierId(), route.getVehicleId(),
                    route.getPlannedDepartureAt(), route.getPlannedArrivalAt(), route.getDeliveryWindowStart(),
                    route.getDeliveryWindowEnd(), route.isActive(), stops.stream().map(RouteStopResponse::from).toList(),
                    route.getCreatedAt(), route.getUpdatedAt());
        }
    }

    public record ShipmentLineRequest(
            @Positive int sequence,
            UUID productId,
            @Size(max = 100) String productCodeSnapshot,
            @NotBlank @Size(max = 300) String productNameSnapshot,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal quantity,
            @NotBlank @Size(max = 30) String unitOfMeasureSnapshot,
            UUID sourceDocumentId,
            @Size(max = 80) String sourceDocumentType,
            @Size(max = 100) String sourceDocumentNumberSnapshot
    ) {
    }

    public record ShipmentRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_./-]{0,79}$") String shipmentNumber,
            @Size(max = 500) String origin,
            @Size(max = 500) String destination,
            UUID carrierId,
            UUID vehicleId,
            UUID routeId,
            Instant plannedDepartureAt,
            Instant plannedArrivalAt,
            @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal freightCost,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currencyCode,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal totalWeightKg,
            @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal totalVolumeM3,
            @NotEmpty @Size(max = 1000) List<@Valid ShipmentLineRequest> lines
    ) {
    }

    public record ShipmentLineResponse(
            UUID id, int sequence, UUID productId, String productCodeSnapshot, String productNameSnapshot,
            BigDecimal quantity, String unitOfMeasureSnapshot, UUID sourceDocumentId,
            String sourceDocumentType, String sourceDocumentNumberSnapshot
    ) {
        static ShipmentLineResponse from(ShipmentLine line) {
            return new ShipmentLineResponse(line.getId(), line.getLineSequence(), line.getProductId(),
                    line.getProductCodeSnapshot(), line.getProductNameSnapshot(), line.getQuantity(),
                    line.getUnitOfMeasureSnapshot(), line.getSourceDocumentId(), line.getSourceDocumentType(),
                    line.getSourceDocumentNumberSnapshot());
        }
    }

    public record ShipmentDocumentRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$") String documentType,
            @NotBlank @Size(max = 255) String originalFileName,
            @NotBlank @Size(max = 500) String storageKey,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$") @Size(max = 150) String mediaType,
            @NotBlank @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String sha256,
            @PositiveOrZero long sizeBytes
    ) {
    }

    public record ShipmentDocumentResponse(
            UUID id, String documentType, String originalFileName, String storageKey,
            String mediaType, String sha256, long sizeBytes, Instant createdAt
    ) {
        static ShipmentDocumentResponse from(ShipmentDocument document) {
            return new ShipmentDocumentResponse(document.getId(), document.getDocumentType(),
                    document.getOriginalFileName(), document.getStorageKey(), document.getMediaType(),
                    document.getSha256(), document.getSizeBytes(), document.getCreatedAt());
        }
    }

    public record ShipmentResponse(
            UUID id, String shipmentNumber, ShipmentStatus status, ShipmentStatus statusBeforeException,
            String origin, String destination, UUID carrierId, UUID vehicleId, UUID routeId,
            Instant plannedDepartureAt, Instant plannedArrivalAt, Instant actualDepartureAt,
            Instant actualArrivalAt, Instant deliveredAt, BigDecimal freightCost, String currencyCode,
            UUID freightRateId, String freightRateCodeSnapshot, String freightRateNameSnapshot,
            FreightCalculationMethod freightMethodSnapshot, LocalDate freightPricingDateSnapshot,
            BigDecimal freightFixedComponentSnapshot, BigDecimal freightVariableComponentSnapshot,
            BigDecimal freightDistanceKmSnapshot, Boolean freightMinimumAppliedSnapshot,
            Boolean freightMaximumAppliedSnapshot,
            BigDecimal totalWeightKg, BigDecimal totalVolumeM3, String statusNote,
            List<ShipmentLineResponse> lines, List<ShipmentDocumentResponse> documents,
            Instant createdAt, Instant updatedAt
    ) {
        static ShipmentResponse from(Shipment shipment, List<ShipmentLine> lines,
                                     List<ShipmentDocument> documents) {
            return new ShipmentResponse(shipment.getId(), shipment.getShipmentNumber(), shipment.getStatus(),
                    shipment.getStatusBeforeException(), shipment.getOriginSnapshot(), shipment.getDestinationSnapshot(),
                    shipment.getCarrierId(), shipment.getVehicleId(), shipment.getRouteId(),
                    shipment.getPlannedDepartureAt(), shipment.getPlannedArrivalAt(), shipment.getActualDepartureAt(),
                    shipment.getActualArrivalAt(), shipment.getDeliveredAt(), shipment.getFreightCost(),
                    shipment.getCurrencyCode(), shipment.getFreightRateId(), shipment.getFreightRateCodeSnapshot(),
                    shipment.getFreightRateNameSnapshot(), shipment.getFreightMethodSnapshot(),
                    shipment.getFreightPricingDateSnapshot(), shipment.getFreightFixedComponentSnapshot(),
                    shipment.getFreightVariableComponentSnapshot(), shipment.getFreightDistanceKmSnapshot(),
                    shipment.getFreightMinimumAppliedSnapshot(), shipment.getFreightMaximumAppliedSnapshot(),
                    shipment.getTotalWeightKg(), shipment.getTotalVolumeM3(),
                    shipment.getStatusNote(), lines.stream().map(ShipmentLineResponse::from).toList(),
                    documents.stream().map(ShipmentDocumentResponse::from).toList(), shipment.getCreatedAt(),
                    shipment.getUpdatedAt());
        }
    }

    public record TransitionTimeRequest(Instant occurredAt) {
    }

    public record StatusNoteRequest(@NotBlank @Size(max = 1000) String reason) {
    }
}
