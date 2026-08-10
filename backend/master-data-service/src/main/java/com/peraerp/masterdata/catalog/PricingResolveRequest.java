package com.peraerp.masterdata.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PricingResolveRequest(
        UUID customerId,
        UUID productId,
        UUID productNatureId,
        UUID productSupertypeId,
        UUID productTypeId,
        UUID productGroupId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6)
        BigDecimal quantity,
        @NotNull LocalDate date,
        @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal basePrice,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String currency
) {}
