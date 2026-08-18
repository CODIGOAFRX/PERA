package com.peraerp.operations.workflow;

import com.peraerp.operations.config.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/work-executions")
public class WorkExecutionController {

    private final WorkExecutionService service;

    public WorkExecutionController(WorkExecutionService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<WorkExecutionResponse> search(@RequestParam(required = false) WorkExecutionStatus status,
                                               @RequestParam(required = false) String referenceType,
                                               @RequestParam(required = false) UUID referenceId,
                                               Pageable pageable) {
        return PageResponse.from(service.search(status, referenceType, referenceId, pageable));
    }

    @GetMapping("/{id}")
    WorkExecutionResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WorkExecutionResponse create(@Valid @RequestBody CreateWorkExecutionRequest request) {
        return service.create(request);
    }

    @PostMapping("/{executionId}/steps/{stepId}/start")
    WorkExecutionResponse startStep(@PathVariable UUID executionId, @PathVariable UUID stepId) {
        return service.startStep(executionId, stepId);
    }

    @PostMapping("/{executionId}/steps/{stepId}/complete")
    WorkExecutionResponse completeStep(@PathVariable UUID executionId, @PathVariable UUID stepId,
                                       @Valid @RequestBody(required = false) StepTransitionRequest request) {
        return service.completeStep(executionId, stepId, note(request));
    }

    @PostMapping("/{executionId}/steps/{stepId}/skip")
    WorkExecutionResponse skipStep(@PathVariable UUID executionId, @PathVariable UUID stepId,
                                   @Valid @RequestBody(required = false) StepTransitionRequest request) {
        return service.skipStep(executionId, stepId, note(request));
    }

    @PostMapping("/{executionId}/steps/{stepId}/cancel")
    WorkExecutionResponse cancelStep(@PathVariable UUID executionId, @PathVariable UUID stepId,
                                     @Valid @RequestBody(required = false) StepTransitionRequest request) {
        return service.cancelFromStep(executionId, stepId, note(request));
    }

    private String note(StepTransitionRequest request) {
        return request == null ? null : request.note();
    }
}
