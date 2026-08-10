package com.peraerp.operations.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkExecutionResponse(UUID id, UUID templateId, String templateCode, String templateName,
                                    int templateVersion, String referenceType, UUID referenceId,
                                    WorkExecutionStatus status, Instant startedAt, Instant completedAt,
                                    Instant cancelledAt, List<WorkStepResponse> steps) {
    static WorkExecutionResponse from(WorkExecution execution) {
        return new WorkExecutionResponse(execution.getId(), execution.getTemplateId(),
                execution.getTemplateCodeSnapshot(), execution.getTemplateNameSnapshot(),
                execution.getTemplateVersionSnapshot(), execution.getReferenceType(), execution.getReferenceId(),
                execution.getStatus(), execution.getStartedAt(), execution.getCompletedAt(),
                execution.getCancelledAt(), execution.getSteps().stream().map(WorkStepResponse::from).toList());
    }
}
