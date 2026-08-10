package com.peraerp.operations.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record CreateWorkExecutionRequest(
        @NotNull UUID templateId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$") String referenceType,
        @NotNull UUID referenceId
) {
}
