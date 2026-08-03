package com.peraerp.masterdata.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 180) String name,
        String description,
        UUID productTypeId,
        UUID familyId,
        UUID categoryId,
        @NotNull UnitOfMeasure unitOfMeasure,
        @NotNull @DecimalMin("0") BigDecimal basePrice,
        @NotNull @DecimalMin("0") BigDecimal taxRate,
        boolean active
) {}
