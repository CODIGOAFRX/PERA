package com.peraerp.masterdata.packaging;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PackagingTypeRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 140) String name,
        @Size(max = 4000) String description,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal internalLength,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal internalWidth,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal internalHeight,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal externalLength,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal externalWidth,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal externalHeight,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal tareWeight,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 4) BigDecimal maximumWeight,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal maximumVolume,
        boolean returnable,
        boolean active
) {}
