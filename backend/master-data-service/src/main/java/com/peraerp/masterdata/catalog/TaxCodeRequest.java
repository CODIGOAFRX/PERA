package com.peraerp.masterdata.catalog;

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
        boolean exempt,
        boolean active
) {}
