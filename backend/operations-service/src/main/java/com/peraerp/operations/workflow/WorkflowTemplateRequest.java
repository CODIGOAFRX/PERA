package com.peraerp.operations.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record WorkflowTemplateRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,59}$") String code,
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_.-]{0,79}$") String referenceType,
        @NotEmpty @Size(max = 100) List<@Valid WorkflowStepRequest> steps
) {
}
