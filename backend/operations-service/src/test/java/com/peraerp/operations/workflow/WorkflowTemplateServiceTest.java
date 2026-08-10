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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTemplateServiceTest {

    @Mock WorkflowTemplateRepository repository;
    @Mock WorkExecutionRepository executionRepository;
    @Mock CurrentCompanyProvider companyProvider;

    private WorkflowTemplateService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new WorkflowTemplateService(repository, executionRepository, companyProvider);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsStepsInTheirConfiguredOrder() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "STANDARD_FLOW")).thenReturn(false);
        when(repository.save(any(WorkflowTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));
        WorkflowTemplateRequest request = new WorkflowTemplateRequest("standard_flow", "Flujo estándar", "product",
                List.of(step("DELIVER", "Entregar", 2, false), step("PREPARE", "Preparar", 1, true)));

        WorkflowTemplateResponse response = service.create(request);

        assertThat(response.code()).isEqualTo("STANDARD_FLOW");
        assertThat(response.referenceType()).isEqualTo("PRODUCT");
        assertThat(response.status()).isEqualTo(WorkflowTemplateStatus.DRAFT);
        assertThat(response.steps()).extracting(WorkflowStepResponse::code)
                .containsExactly("PREPARE", "DELIVER");
    }

    @Test
    void rejectsGapsAndDuplicateStepCodes() {
        WorkflowTemplateRequest gap = new WorkflowTemplateRequest("FLOW", "Flujo", "PRODUCT",
                List.of(step("ONE", "Uno", 1, true), step("THREE", "Tres", 3, true)));
        assertThatThrownBy(() -> service.create(gap))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("continua");

        WorkflowTemplateRequest duplicate = new WorkflowTemplateRequest("FLOW", "Flujo", "PRODUCT",
                List.of(step("SAME", "Uno", 1, true), step("same", "Dos", 2, true)));
        assertThatThrownBy(() -> service.create(duplicate))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se pueden repetir");
    }

    @Test
    void publishedVersionIsImmutableAndCanCreateANewDraftVersion() {
        WorkflowTemplate template = template();
        when(repository.findByIdAndCompanyId(template.getId(), companyId)).thenReturn(Optional.of(template));
        when(repository.findMaxVersion(companyId, "STANDARD_FLOW")).thenReturn(1);
        when(repository.save(any(WorkflowTemplate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowTemplateResponse published = service.publish(template.getId());
        assertThat(published.status()).isEqualTo(WorkflowTemplateStatus.PUBLISHED);

        assertThatThrownBy(() -> service.update(template.getId(), request()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inmutables");

        WorkflowTemplateResponse newVersion = service.createVersion(template.getId());
        assertThat(newVersion.templateVersion()).isEqualTo(2);
        assertThat(newVersion.status()).isEqualTo(WorkflowTemplateStatus.DRAFT);
        assertThat(newVersion.steps()).extracting(WorkflowStepResponse::code)
                .containsExactly("PREPARE", "DELIVER");
    }

    @Test
    void tenantLookupNeverFallsBackToAnUnscopedRepositoryCall() {
        UUID foreignTemplateId = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(foreignTemplateId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(foreignTemplateId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(foreignTemplateId.toString());

        verify(repository).findByIdAndCompanyId(foreignTemplateId, companyId);
        verify(repository, never()).findById(foreignTemplateId);
    }

    private WorkflowTemplate template() {
        WorkflowTemplate template = new WorkflowTemplate(companyId, "STANDARD_FLOW", "Flujo estándar", "PRODUCT", 1);
        ReflectionTestUtils.setField(template, "id", UUID.randomUUID());
        template.addStep(new WorkflowStepDefinition("PREPARE", "Preparar", null, 1, true, 30));
        template.addStep(new WorkflowStepDefinition("DELIVER", "Entregar", null, 2, false, 15));
        return template;
    }

    private WorkflowTemplateRequest request() {
        return new WorkflowTemplateRequest("STANDARD_FLOW", "Flujo modificado", "PRODUCT",
                List.of(step("PREPARE", "Preparar", 1, true), step("DELIVER", "Entregar", 2, false)));
    }

    private WorkflowStepRequest step(String code, String name, int sequence, boolean required) {
        return new WorkflowStepRequest(code, name, null, sequence, required, 10);
    }
}
