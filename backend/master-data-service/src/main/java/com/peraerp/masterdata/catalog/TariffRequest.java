package com.peraerp.masterdata.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TariffRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 140) String name,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String currency,
        @NotNull LocalDate validFrom,
        LocalDate validUntil,
        boolean active,
        @Min(0) int priority,
        @NotNull PricingScope scope,
        UUID customerId,
        UUID productNatureId,
        UUID productSupertypeId,
        UUID productTypeId,
        UUID productGroupId,
        UUID productId,
        UUID parentTariffId,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
        BigDecimal generalSurchargePercentage,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
        BigDecimal energySurchargePercentage,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal minimumBillingAmount,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal unitMultiple,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal minimumPerPiece
) {}
