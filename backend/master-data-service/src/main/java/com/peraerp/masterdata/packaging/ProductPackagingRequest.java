package com.peraerp.masterdata.packaging;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPackagingRequest(
        @NotNull UUID productId,
        @NotNull UUID packagingTypeId,
        @Size(max = 80) String code,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6)
        BigDecimal unitsPerPackage,
        @Min(1) Integer levels,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6)
        BigDecimal unitsPerLevel,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal length,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal width,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal height,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal grossWeight,
        boolean defaultPackaging,
        boolean active
) {}
