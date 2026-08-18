package com.peraerp.operations.workflow;

import java.time.Instant;
import java.util.UUID;

public record WorkStepResponse(UUID id, String code, String name, String description, int sequence,
                               boolean required, Integer estimatedMinutes, WorkStepStatus status,
                               Instant startedAt, Instant finishedAt, String note) {
    static WorkStepResponse from(WorkStepExecution step) {
        return new WorkStepResponse(step.getId(), step.getCode(), step.getName(), step.getDescription(),
                step.getStepSequence(), step.isRequired(), step.getEstimatedMinutes(), step.getStatus(),
                step.getStartedAt(), step.getFinishedAt(), step.getNote());
    }
}
