package com.peraerp.operations.workflow;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkflowStepRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,59}$") String code,
        @NotBlank @Size(max = 180) String name,
        @Size(max = 2000) String description,
        @Min(1) int sequence,
        boolean required,
        @Min(0) Integer estimatedMinutes
) {
}
