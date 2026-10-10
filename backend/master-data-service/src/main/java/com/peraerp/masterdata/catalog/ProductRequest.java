package com.peraerp.masterdata.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        @NotBlank @Size(max = 60) String code,
        @NotBlank @Size(max = 180) String name,
        String description,
        UUID productTypeId,
        UUID productGroupId,
        UUID taxCodeId,
        UUID familyId,
        UUID categoryId,
        @NotNull UnitOfMeasure unitOfMeasure,
        @NotNull @DecimalMin("0") BigDecimal basePrice,
        @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
        boolean active,
        /* Nulo conserva el código de barras guardado; una cadena vacía lo quita. */
        @Size(max = 64) @Pattern(regexp = "^$|^[0-9A-Za-z][0-9A-Za-z./+ -]{0,63}$") String barcode
) {
    public ProductRequest(String code, String name, String description, UUID productTypeId, UUID productGroupId,
                          UUID taxCodeId, UUID familyId, UUID categoryId, UnitOfMeasure unitOfMeasure,
                          BigDecimal basePrice, BigDecimal taxRate, boolean active) {
        this(code, name, description, productTypeId, productGroupId, taxCodeId, familyId, categoryId, unitOfMeasure,
                basePrice, taxRate, active, null);
    }

    public ProductRequest(String code, String name, String description, UUID productTypeId, UUID familyId,
                          UUID categoryId, UnitOfMeasure unitOfMeasure, BigDecimal basePrice, BigDecimal taxRate,
                          boolean active) {
        this(code, name, description, productTypeId, null, null, familyId, categoryId, unitOfMeasure, basePrice,
                taxRate, active);
    }

    public ProductRequest(String code, String name, String description, UUID productTypeId, UUID productGroupId,
                          UUID familyId, UUID categoryId, UnitOfMeasure unitOfMeasure, BigDecimal basePrice,
                          BigDecimal taxRate, boolean active) {
        this(code, name, description, productTypeId, productGroupId, null, familyId, categoryId, unitOfMeasure,
                basePrice, taxRate, active);
    }
}
