package com.peraerp.operations.workflow;

import java.util.UUID;

public record WorkflowStepResponse(UUID id, String code, String name, String description, int sequence,
                                   boolean required, Integer estimatedMinutes) {
    static WorkflowStepResponse from(WorkflowStepDefinition step) {
        return new WorkflowStepResponse(step.getId(), step.getCode(), step.getName(), step.getDescription(),
                step.getStepSequence(), step.isRequired(), step.getEstimatedMinutes());
    }
}
