package com.peraerp.operations.workflow;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WorkflowTemplateService {

    private final WorkflowTemplateRepository repository;
    private final WorkExecutionRepository executionRepository;
    private final CurrentCompanyProvider companyProvider;

    public WorkflowTemplateService(WorkflowTemplateRepository repository,
                                   WorkExecutionRepository executionRepository,
                                   CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.executionRepository = executionRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public WorkflowTemplateResponse create(WorkflowTemplateRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe una plantilla de workflow con el código " + code + ".");
        }
        WorkflowTemplate template = new WorkflowTemplate(companyId, code, request.name().trim(),
                normalizeReferenceType(request.referenceType()), 1);
        normalizedSteps(request.steps()).forEach(template::addStep);
        return WorkflowTemplateResponse.from(repository.save(template));
    }

    @Transactional(readOnly = true)
    public Page<WorkflowTemplateResponse> search(WorkflowTemplateStatus status, String referenceType,
                                                 String query, Pageable pageable) {
        String normalizedReference = referenceType == null || referenceType.isBlank()
                ? null : normalizeReferenceType(referenceType);
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        return repository.search(companyProvider.requireCompanyId(), status != null, status,
                normalizedReference != null, normalizedReference, normalizedQuery != null,
                normalizedQuery == null ? "" : normalizedQuery, pageable).map(WorkflowTemplateResponse::from);
    }

    @Transactional(readOnly = true)
    public WorkflowTemplateResponse findById(UUID id) {
        return WorkflowTemplateResponse.from(requireTemplate(id));
    }

    @Transactional
    public WorkflowTemplateResponse update(UUID id, WorkflowTemplateRequest request) {
        WorkflowTemplate template = requireTemplate(id);
        if (!template.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new BusinessRuleException("El código de una plantilla no se puede modificar.");
        }
        try {
            List<WorkflowStepDefinition> replacementSteps = normalizedSteps(request.steps());
            template.update(request.name().trim(), normalizeReferenceType(request.referenceType()), List.of());
            repository.flush();
            replacementSteps.forEach(template::addStep);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException("Las versiones publicadas o retiradas son inmutables.");
        }
        return WorkflowTemplateResponse.from(template);
    }

    @Transactional
    public WorkflowTemplateResponse publish(UUID id) {
        WorkflowTemplate template = requireTemplate(id);
        try {
            template.publish();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException("Solo se puede publicar una versión borrador con pasos.");
        }
        return WorkflowTemplateResponse.from(template);
    }

    @Transactional
    public WorkflowTemplateResponse createVersion(UUID id) {
        WorkflowTemplate source = requireTemplate(id);
        if (source.getStatus() == WorkflowTemplateStatus.DRAFT) {
            throw new BusinessRuleException("Publica la versión actual antes de crear una nueva.");
        }
        Integer currentMax = repository.findMaxVersion(source.getCompanyId(), source.getCode());
        int nextVersion = (currentMax == null ? source.getTemplateVersion() : currentMax) + 1;
        WorkflowTemplate version = new WorkflowTemplate(source.getCompanyId(), source.getCode(), source.getName(),
                source.getReferenceType(), nextVersion);
        source.getSteps().stream().map(this::copyStep).forEach(version::addStep);
        return WorkflowTemplateResponse.from(repository.save(version));
    }

    @Transactional
    public WorkflowTemplateResponse retire(UUID id) {
        WorkflowTemplate template = requireTemplate(id);
        try {
            template.retire();
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException("Solo se puede retirar una versión publicada.");
        }
        return WorkflowTemplateResponse.from(template);
    }

    @Transactional
    public void delete(UUID id) {
        WorkflowTemplate template = requireTemplate(id);
        if (template.getStatus() != WorkflowTemplateStatus.DRAFT) {
            throw new BusinessRuleException("Solo se pueden eliminar versiones borrador.");
        }
        if (executionRepository.existsByCompanyIdAndTemplateId(template.getCompanyId(), template.getId())) {
            throw new BusinessRuleException("La plantilla ya tiene ejecuciones asociadas.");
        }
        repository.delete(template);
    }

    private WorkflowTemplate requireTemplate(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de workflow", id));
    }

    private List<WorkflowStepDefinition> normalizedSteps(List<WorkflowStepRequest> requests) {
        List<WorkflowStepRequest> sorted = requests.stream()
                .sorted(Comparator.comparingInt(WorkflowStepRequest::sequence))
                .toList();
        var codes = new HashSet<String>();
        for (int index = 0; index < sorted.size(); index++) {
            WorkflowStepRequest step = sorted.get(index);
            if (step.sequence() != index + 1) {
                throw new BusinessRuleException("La secuencia de pasos debe ser continua y comenzar en 1.");
            }
            if (!codes.add(normalizeCode(step.code()))) {
                throw new BusinessRuleException("Los códigos de paso no se pueden repetir.");
            }
        }
        return sorted.stream().map(step -> new WorkflowStepDefinition(normalizeCode(step.code()), step.name().trim(),
                normalizeNullable(step.description()), step.sequence(), step.required(), step.estimatedMinutes())).toList();
    }

    private WorkflowStepDefinition copyStep(WorkflowStepDefinition source) {
        return new WorkflowStepDefinition(source.getCode(), source.getName(), source.getDescription(),
                source.getStepSequence(), source.isRequired(), source.getEstimatedMinutes());
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeReferenceType(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
