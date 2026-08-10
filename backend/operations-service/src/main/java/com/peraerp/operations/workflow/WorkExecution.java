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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "work_executions", uniqueConstraints = @UniqueConstraint(
        name = "uk_work_execution_reference",
        columnNames = {"company_id", "template_id", "reference_type", "reference_id"}))
public class WorkExecution extends CompanyScopedEntity {

    @Column(name = "template_id", nullable = false, updatable = false)
    private UUID templateId;
    @Column(name = "template_code_snapshot", nullable = false, length = 60, updatable = false)
    private String templateCodeSnapshot;
    @Column(name = "template_name_snapshot", nullable = false, length = 180, updatable = false)
    private String templateNameSnapshot;
    @Column(name = "template_version_snapshot", nullable = false, updatable = false)
    private int templateVersionSnapshot;
    @Column(name = "reference_type", nullable = false, length = 80, updatable = false)
    private String referenceType;
    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkExecutionStatus status = WorkExecutionStatus.PENDING;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepSequence ASC")
    private List<WorkStepExecution> steps = new ArrayList<>();

    protected WorkExecution() {
    }

    public WorkExecution(UUID companyId, WorkflowTemplate template, UUID referenceId) {
        super(companyId);
        this.templateId = template.getId();
        this.templateCodeSnapshot = template.getCode();
        this.templateNameSnapshot = template.getName();
        this.templateVersionSnapshot = template.getTemplateVersion();
        this.referenceType = template.getReferenceType();
        this.referenceId = referenceId;
        template.getSteps().forEach(step -> addStep(WorkStepExecution.snapshotOf(step)));
    }

    private void addStep(WorkStepExecution step) {
        step.attachTo(this);
        steps.add(step);
    }

    public void startStep(UUID stepId, Instant occurredAt) {
        requireActive();
        WorkStepExecution step = requireStep(stepId);
        requirePredecessorsFinished(step);
        step.start(occurredAt);
        if (status == WorkExecutionStatus.PENDING) {
            status = WorkExecutionStatus.IN_PROGRESS;
            startedAt = occurredAt;
        }
    }

    public void completeStep(UUID stepId, String note, Instant occurredAt) {
        requireActive();
        WorkStepExecution step = requireStep(stepId);
        step.complete(note, occurredAt);
        completeExecutionWhenFinished(occurredAt);
    }

    public void skipStep(UUID stepId, String note, Instant occurredAt) {
        requireActive();
        WorkStepExecution step = requireStep(stepId);
        requirePredecessorsFinished(step);
        step.skip(note, occurredAt);
        if (status == WorkExecutionStatus.PENDING) {
            status = WorkExecutionStatus.IN_PROGRESS;
            startedAt = occurredAt;
        }
        completeExecutionWhenFinished(occurredAt);
    }

    public void cancelFromStep(UUID stepId, String note, Instant occurredAt) {
        requireActive();
        WorkStepExecution selected = requireStep(stepId);
        selected.cancel(note, occurredAt);
        steps.stream().filter(step -> !step.getStatus().isTerminal()).forEach(step -> step.cancel(note, occurredAt));
        status = WorkExecutionStatus.CANCELLED;
        cancelledAt = occurredAt;
    }

    private WorkStepExecution requireStep(UUID stepId) {
        return steps.stream().filter(step -> stepId.equals(step.getId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("The step does not belong to this execution."));
    }

    private void requirePredecessorsFinished(WorkStepExecution selected) {
        boolean unfinishedPredecessor = steps.stream()
                .filter(step -> step.getStepSequence() < selected.getStepSequence())
                .anyMatch(step -> !step.getStatus().isTerminal());
        if (unfinishedPredecessor) {
            throw new IllegalStateException("Previous workflow steps must be finished first.");
        }
    }

    private void completeExecutionWhenFinished(Instant occurredAt) {
        if (steps.stream().allMatch(step -> step.getStatus() == WorkStepStatus.COMPLETED
                || step.getStatus() == WorkStepStatus.SKIPPED)) {
            status = WorkExecutionStatus.COMPLETED;
            completedAt = occurredAt;
        }
    }

    private void requireActive() {
        if (status == WorkExecutionStatus.COMPLETED || status == WorkExecutionStatus.CANCELLED) {
            throw new IllegalStateException("The work execution is already closed.");
        }
    }

    public UUID getTemplateId() { return templateId; }
    public String getTemplateCodeSnapshot() { return templateCodeSnapshot; }
    public String getTemplateNameSnapshot() { return templateNameSnapshot; }
    public int getTemplateVersionSnapshot() { return templateVersionSnapshot; }
    public String getReferenceType() { return referenceType; }
    public UUID getReferenceId() { return referenceId; }
    public WorkExecutionStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public List<WorkStepExecution> getSteps() { return List.copyOf(steps); }
}
