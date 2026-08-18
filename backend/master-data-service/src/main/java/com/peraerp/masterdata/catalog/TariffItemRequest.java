package com.peraerp.masterdata.catalog;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TariffItemRequest(
        @NotNull UUID productId,
        UUID customerId,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal price,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
        BigDecimal discountPercentage,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
        BigDecimal surchargePercentage,
        @Min(0) int priority,
        @NotNull LocalDate validFrom,
        LocalDate validUntil,
        boolean active
) {}
