package com.peraerp.operations.workflow;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowTemplateResponse(UUID id, String code, String name, String referenceType,
                                       WorkflowTemplateStatus status, int templateVersion,
                                       List<WorkflowStepResponse> steps, Instant createdAt, Instant updatedAt) {
    static WorkflowTemplateResponse from(WorkflowTemplate template) {
        return new WorkflowTemplateResponse(template.getId(), template.getCode(), template.getName(),
                template.getReferenceType(), template.getStatus(), template.getTemplateVersion(),
                template.getSteps().stream().map(WorkflowStepResponse::from).toList(),
                template.getCreatedAt(), template.getUpdatedAt());
    }
}
