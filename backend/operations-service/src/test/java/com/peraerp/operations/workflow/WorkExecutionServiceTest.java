package com.peraerp.operations.workflow;

import com.peraerp.operations.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkExecutionServiceTest {

    @Mock WorkExecutionRepository repository;
    @Mock WorkflowTemplateRepository templateRepository;
    @Mock CurrentCompanyProvider companyProvider;

    private WorkExecutionService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new WorkExecutionService(repository, templateRepository, companyProvider);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void executionSnapshotsAPublishedTemplate() {
        WorkflowTemplate template = publishedTemplate();
        UUID referenceId = UUID.randomUUID();
        when(templateRepository.findByIdAndCompanyId(template.getId(), companyId)).thenReturn(Optional.of(template));
        when(repository.existsByCompanyIdAndTemplateIdAndReferenceTypeAndReferenceId(
                companyId, template.getId(), "PRODUCT", referenceId)).thenReturn(false);
        when(repository.save(any(WorkExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkExecutionResponse response = service.create(
                new CreateWorkExecutionRequest(template.getId(), "product", referenceId));

        assertThat(response.templateCode()).isEqualTo("STANDARD_FLOW");
        assertThat(response.templateVersion()).isEqualTo(1);
        assertThat(response.referenceType()).isEqualTo("PRODUCT");
        assertThat(response.status()).isEqualTo(WorkExecutionStatus.PENDING);
        assertThat(response.steps()).extracting(WorkStepResponse::code)
                .containsExactly("PREPARE", "CHECK", "DELIVER");
    }

    @Test
    void enforcesOrderAndValidStepTransitionsUntilCompletion() {
        WorkExecution execution = execution();
        UUID first = execution.getSteps().get(0).getId();
        UUID optional = execution.getSteps().get(1).getId();
        UUID last = execution.getSteps().get(2).getId();
        when(repository.findByIdAndCompanyId(execution.getId(), companyId)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> service.startStep(execution.getId(), last))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pasos anteriores");

        service.startStep(execution.getId(), first);
        service.completeStep(execution.getId(), first, "Preparado");
        service.skipStep(execution.getId(), optional, "No aplica");
        service.startStep(execution.getId(), last);
        WorkExecutionResponse completed = service.completeStep(execution.getId(), last, "Entregado");

        assertThat(completed.status()).isEqualTo(WorkExecutionStatus.COMPLETED);
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.steps()).extracting(WorkStepResponse::status)
                .containsExactly(WorkStepStatus.COMPLETED, WorkStepStatus.SKIPPED, WorkStepStatus.COMPLETED);
    }

    @Test
    void rejectsSkippingARequiredStepAndCancelsRemainingWorkFromAStep() {
        WorkExecution execution = execution();
        UUID first = execution.getSteps().get(0).getId();
        when(repository.findByIdAndCompanyId(execution.getId(), companyId)).thenReturn(Optional.of(execution));

        assertThatThrownBy(() -> service.skipStep(execution.getId(), first, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("obligatorio");

        WorkExecutionResponse cancelled = service.cancelFromStep(execution.getId(), first, "Trabajo cancelado");
        assertThat(cancelled.status()).isEqualTo(WorkExecutionStatus.CANCELLED);
        assertThat(cancelled.steps()).extracting(WorkStepResponse::status)
                .containsOnly(WorkStepStatus.CANCELLED);
    }

    @Test
    void executionLookupIsAlwaysTenantScoped() {
        UUID foreignExecutionId = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(foreignExecutionId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(foreignExecutionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(foreignExecutionId.toString());

        verify(repository).findByIdAndCompanyId(foreignExecutionId, companyId);
        verify(repository, never()).findById(foreignExecutionId);
    }

    private WorkflowTemplate publishedTemplate() {
        WorkflowTemplate template = new WorkflowTemplate(companyId, "STANDARD_FLOW", "Flujo estándar", "PRODUCT", 1);
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        template.addStep(new WorkflowStepDefinition("PREPARE", "Preparar", null, 1, true, 30));
        template.addStep(new WorkflowStepDefinition("CHECK", "Comprobar", null, 2, false, 10));
        template.addStep(new WorkflowStepDefinition("DELIVER", "Entregar", null, 3, true, 20));
        template.publish();
        return template;
    }

    private WorkExecution execution() {
        WorkExecution execution = new WorkExecution(companyId, publishedTemplate(), UUID.randomUUID());
        ReflectionTestUtils.setField(execution, "id", UUID.randomUUID());
        execution.getSteps().forEach(step -> ReflectionTestUtils.setField(step, "id", UUID.randomUUID()));
        return execution;
    }
}
