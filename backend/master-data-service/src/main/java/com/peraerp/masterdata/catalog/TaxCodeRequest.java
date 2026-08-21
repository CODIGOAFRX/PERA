package com.peraerp.masterdata.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxCodeRequest(
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{2}") String countryCode,
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 140) String name,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal percentage,
        @NotNull LocalDate validFrom,
        LocalDate validUntil,
        @Schema(description = "Se conserva por compatibilidad. Si se envía operationQualification, manda esta última.",
                deprecated = true)
        boolean exempt,
        @Schema(description = "CalificacionOperacion de Veri*Factu. EXEMPT exige indicar la causa.")
        OperationQualification operationQualification,
        @Schema(description = "OperacionExenta de Veri*Factu (E1-E6). Obligatoria si la operación es exenta.")
        ExemptionCause exemptionCause,
        @Schema(description = "ClaveRegimen de Veri*Factu. 01 es el régimen general.")
        @Pattern(regexp = "\\d{2}") String regimeKey,
        boolean active
) {}
