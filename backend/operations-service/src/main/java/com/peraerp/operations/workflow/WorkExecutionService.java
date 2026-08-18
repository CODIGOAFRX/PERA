package com.peraerp.operations.workflow;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class WorkExecutionService {

    private final WorkExecutionRepository repository;
    private final WorkflowTemplateRepository templateRepository;
    private final CurrentCompanyProvider companyProvider;

    public WorkExecutionService(WorkExecutionRepository repository, WorkflowTemplateRepository templateRepository,
                                CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.templateRepository = templateRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public WorkExecutionResponse create(CreateWorkExecutionRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        WorkflowTemplate template = templateRepository.findByIdAndCompanyId(request.templateId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Plantilla de workflow", request.templateId()));
        if (template.getStatus() != WorkflowTemplateStatus.PUBLISHED) {
            throw new BusinessRuleException("Solo se pueden ejecutar versiones publicadas.");
        }
        String referenceType = normalizeReferenceType(request.referenceType());
        if (!template.getReferenceType().equals(referenceType)) {
            throw new BusinessRuleException("El tipo de referencia no coincide con el de la plantilla.");
        }
        if (repository.existsByCompanyIdAndTemplateIdAndReferenceTypeAndReferenceId(
                companyId, template.getId(), referenceType, request.referenceId())) {
            throw new BusinessRuleException("Ya existe una ejecución de esta plantilla para la referencia indicada.");
        }
        return WorkExecutionResponse.from(repository.save(new WorkExecution(companyId, template, request.referenceId())));
    }

    @Transactional(readOnly = true)
    public Page<WorkExecutionResponse> search(WorkExecutionStatus status, String referenceType, UUID referenceId,
                                              Pageable pageable) {
        String normalizedReference = referenceType == null || referenceType.isBlank()
                ? null : normalizeReferenceType(referenceType);
        return repository.search(companyProvider.requireCompanyId(), status != null, status,
                        normalizedReference != null, normalizedReference, referenceId != null, referenceId, pageable)
                .map(WorkExecutionResponse::from);
    }

    @Transactional(readOnly = true)
    public WorkExecutionResponse findById(UUID id) {
        return WorkExecutionResponse.from(requireExecution(id));
    }

    @Transactional
    public WorkExecutionResponse startStep(UUID executionId, UUID stepId) {
        return transition(executionId, stepId, execution -> execution.startStep(stepId, Instant.now()));
    }

    @Transactional
    public WorkExecutionResponse completeStep(UUID executionId, UUID stepId, String note) {
        return transition(executionId, stepId, execution -> execution.completeStep(stepId, note, Instant.now()));
    }

    @Transactional
    public WorkExecutionResponse skipStep(UUID executionId, UUID stepId, String note) {
        return transition(executionId, stepId, execution -> execution.skipStep(stepId, note, Instant.now()));
    }

    @Transactional
    public WorkExecutionResponse cancelFromStep(UUID executionId, UUID stepId, String note) {
        return transition(executionId, stepId, execution -> execution.cancelFromStep(stepId, note, Instant.now()));
    }

    private WorkExecutionResponse transition(UUID executionId, UUID stepId, Consumer<WorkExecution> command) {
        WorkExecution execution = requireExecution(executionId);
        try {
            command.accept(execution);
        } catch (IllegalArgumentException exception) {
            throw new ResourceNotFoundException("Paso de ejecución", stepId);
        } catch (IllegalStateException exception) {
            throw new BusinessRuleException(translateTransitionError(exception.getMessage()));
        }
        return WorkExecutionResponse.from(execution);
    }

    private WorkExecution requireExecution(UUID id) {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Ejecución de trabajo", id));
    }

    private String translateTransitionError(String message) {
        return switch (message) {
            case "Previous workflow steps must be finished first." ->
                    "Los pasos anteriores deben finalizarse antes de continuar.";
            case "Only a pending step can be started." -> "Solo se puede iniciar un paso pendiente.";
            case "Only an in-progress step can be completed." -> "Solo se puede completar un paso iniciado.";
            case "A required workflow step cannot be skipped." -> "Un paso obligatorio no se puede omitir.";
            case "Only a pending optional step can be skipped." ->
                    "Solo se puede omitir un paso opcional pendiente.";
            case "A finished workflow step cannot be cancelled." -> "Un paso finalizado no se puede cancelar.";
            case "The work execution is already closed." -> "La ejecución de trabajo ya está cerrada.";
            default -> "La transición solicitada no es válida.";
        };
    }

    private String normalizeReferenceType(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
