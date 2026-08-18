package com.peraerp.operations.freight;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class FreightDtos {

    private FreightDtos() {
    }

    public record FreightRateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,59}$") String code,
            @NotBlank @Size(max = 180) String name,
            UUID routeId,
            UUID carrierId,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currencyCode,
            @NotNull LocalDate validFrom,
            LocalDate validTo,
            Boolean active,
            @Min(-1000000) @Max(1000000) Integer priority,
            @NotNull FreightCalculationMethod calculationMethod,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal fixedAmount,
            @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal unitAmount,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal minimumCharge,
            @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal maximumCharge,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal minimumWeightKg,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal maximumWeightKg,
            @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal minimumVolumeM3,
            @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal maximumVolumeM3,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal minimumDistanceKm,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal maximumDistanceKm
    ) {
    }

    public record FreightRateResponse(
            UUID id, String code, String name, UUID routeId, UUID carrierId, String currencyCode,
            LocalDate validFrom, LocalDate validTo, boolean active, int priority,
            FreightCalculationMethod calculationMethod, BigDecimal fixedAmount, BigDecimal unitAmount,
            BigDecimal minimumCharge, BigDecimal maximumCharge, BigDecimal minimumWeightKg,
            BigDecimal maximumWeightKg, BigDecimal minimumVolumeM3, BigDecimal maximumVolumeM3,
            BigDecimal minimumDistanceKm, BigDecimal maximumDistanceKm, Instant createdAt, Instant updatedAt
    ) {
        static FreightRateResponse from(FreightRate rate) {
            return new FreightRateResponse(rate.getId(), rate.getCode(), rate.getName(), rate.getRouteId(),
                    rate.getCarrierId(), rate.getCurrencyCode(), rate.getValidFrom(), rate.getValidTo(),
                    rate.isActive(), rate.getPriority(), rate.getCalculationMethod(), rate.getFixedAmount(),
                    rate.getUnitAmount(), rate.getMinimumCharge(), rate.getMaximumCharge(),
                    rate.getMinimumWeightKg(), rate.getMaximumWeightKg(), rate.getMinimumVolumeM3(),
                    rate.getMaximumVolumeM3(), rate.getMinimumDistanceKm(), rate.getMaximumDistanceKm(),
                    rate.getCreatedAt(), rate.getUpdatedAt());
        }
    }

    public record FreightSimulationRequest(
            @NotNull LocalDate pricingDate,
            UUID routeId,
            UUID carrierId,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currencyCode,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal weightKg,
            @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal volumeM3,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal distanceKm
    ) {
    }

    public record ApplyShipmentFreightRequest(
            @NotNull LocalDate pricingDate,
            @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal distanceKm
    ) {
    }

    public record FreightQuoteResponse(
            UUID freightRateId, String rateCode, String rateName, FreightCalculationMethod calculationMethod,
            String currencyCode, LocalDate pricingDate, UUID routeId, UUID carrierId,
            BigDecimal weightKg, BigDecimal volumeM3, BigDecimal distanceKm,
            BigDecimal fixedComponent, BigDecimal variableComponent, BigDecimal amount,
            boolean minimumApplied, boolean maximumApplied, int eligibleRateCount
    ) {
    }
}
