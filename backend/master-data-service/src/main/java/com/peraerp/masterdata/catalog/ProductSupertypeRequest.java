package com.peraerp.masterdata.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ProductSupertypeRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 140) String name,
        @NotNull UUID natureId,
        @NotNull Boolean active
) {}
