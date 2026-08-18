package com.peraerp.operations.workflow;

import com.peraerp.operations.config.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workflow-templates")
public class WorkflowTemplateController {

    private final WorkflowTemplateService service;

    public WorkflowTemplateController(WorkflowTemplateService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<WorkflowTemplateResponse> search(@RequestParam(required = false) WorkflowTemplateStatus status,
                                                  @RequestParam(required = false) String referenceType,
                                                  @RequestParam(required = false) String query,
                                                  Pageable pageable) {
        return PageResponse.from(service.search(status, referenceType, query, pageable));
    }

    @GetMapping("/{id}")
    WorkflowTemplateResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WorkflowTemplateResponse create(@Valid @RequestBody WorkflowTemplateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    WorkflowTemplateResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody WorkflowTemplateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/publish")
    WorkflowTemplateResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    WorkflowTemplateResponse createVersion(@PathVariable UUID id) {
        return service.createVersion(id);
    }

    @PostMapping("/{id}/retire")
    WorkflowTemplateResponse retire(@PathVariable UUID id) {
        return service.retire(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
