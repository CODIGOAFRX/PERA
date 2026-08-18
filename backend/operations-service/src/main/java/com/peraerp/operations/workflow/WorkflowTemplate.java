package com.peraerp.operations.workflow;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow_templates", uniqueConstraints = @UniqueConstraint(
        name = "uk_workflow_template_version", columnNames = {"company_id", "code", "template_version"}))
public class WorkflowTemplate extends CompanyScopedEntity {

    @Column(nullable = false, length = 60, updatable = false)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(name = "reference_type", nullable = false, length = 80)
    private String referenceType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowTemplateStatus status = WorkflowTemplateStatus.DRAFT;
    @Column(name = "template_version", nullable = false, updatable = false)
    private int templateVersion;
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepSequence ASC")
    private List<WorkflowStepDefinition> steps = new ArrayList<>();

    protected WorkflowTemplate() {
    }

    public WorkflowTemplate(UUID companyId, String code, String name, String referenceType, int templateVersion) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.referenceType = referenceType;
        this.templateVersion = templateVersion;
    }

    public void update(String name, String referenceType, List<WorkflowStepDefinition> replacementSteps) {
        requireDraft();
        this.name = name;
        this.referenceType = referenceType;
        this.steps.clear();
        replacementSteps.forEach(this::addStep);
    }

    public void addStep(WorkflowStepDefinition step) {
        requireDraft();
        step.attachTo(this);
        this.steps.add(step);
    }

    public void publish() {
        requireDraft();
        if (steps.isEmpty()) {
            throw new IllegalStateException("A workflow template cannot be published without steps.");
        }
        this.status = WorkflowTemplateStatus.PUBLISHED;
    }

    public void retire() {
        if (status != WorkflowTemplateStatus.PUBLISHED) {
            throw new IllegalStateException("Only a published workflow template can be retired.");
        }
        this.status = WorkflowTemplateStatus.RETIRED;
    }

    private void requireDraft() {
        if (status != WorkflowTemplateStatus.DRAFT) {
            throw new IllegalStateException("Published workflow template versions are immutable.");
        }
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getReferenceType() { return referenceType; }
    public WorkflowTemplateStatus getStatus() { return status; }
    public int getTemplateVersion() { return templateVersion; }
    public List<WorkflowStepDefinition> getSteps() { return List.copyOf(steps); }
}
