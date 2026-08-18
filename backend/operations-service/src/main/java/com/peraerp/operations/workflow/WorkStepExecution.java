package com.peraerp.operations.workflow;

import com.peraerp.platform.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "work_execution_steps", uniqueConstraints = {
        @UniqueConstraint(name = "uk_work_execution_step_code", columnNames = {"execution_id", "code"}),
        @UniqueConstraint(name = "uk_work_execution_step_sequence", columnNames = {"execution_id", "step_sequence"})
})
public class WorkStepExecution extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_id", nullable = false)
    private WorkExecution execution;
    @Column(nullable = false, length = 60, updatable = false)
    private String code;
    @Column(nullable = false, length = 180, updatable = false)
    private String name;
    @Column(columnDefinition = "text", updatable = false)
    private String description;
    @Column(name = "step_sequence", nullable = false, updatable = false)
    private int stepSequence;
    @Column(name = "required_step", nullable = false, updatable = false)
    private boolean required;
    @Column(name = "estimated_minutes", updatable = false)
    private Integer estimatedMinutes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkStepStatus status = WorkStepStatus.PENDING;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(columnDefinition = "text")
    private String note;

    protected WorkStepExecution() {
    }

    private WorkStepExecution(WorkflowStepDefinition definition) {
        this.code = definition.getCode();
        this.name = definition.getName();
        this.description = definition.getDescription();
        this.stepSequence = definition.getStepSequence();
        this.required = definition.isRequired();
        this.estimatedMinutes = definition.getEstimatedMinutes();
    }

    static WorkStepExecution snapshotOf(WorkflowStepDefinition definition) {
        return new WorkStepExecution(definition);
    }

    void attachTo(WorkExecution execution) {
        this.execution = execution;
    }

    void start(Instant occurredAt) {
        if (status != WorkStepStatus.PENDING) {
            throw new IllegalStateException("Only a pending step can be started.");
        }
        status = WorkStepStatus.IN_PROGRESS;
        startedAt = occurredAt;
    }

    void complete(String note, Instant occurredAt) {
        if (status != WorkStepStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only an in-progress step can be completed.");
        }
        status = WorkStepStatus.COMPLETED;
        finishedAt = occurredAt;
        this.note = normalizeNote(note);
    }

    void skip(String note, Instant occurredAt) {
        if (required) {
            throw new IllegalStateException("A required workflow step cannot be skipped.");
        }
        if (status != WorkStepStatus.PENDING) {
            throw new IllegalStateException("Only a pending optional step can be skipped.");
        }
        status = WorkStepStatus.SKIPPED;
        finishedAt = occurredAt;
        this.note = normalizeNote(note);
    }

    void cancel(String note, Instant occurredAt) {
        if (status.isTerminal()) {
            throw new IllegalStateException("A finished workflow step cannot be cancelled.");
        }
        status = WorkStepStatus.CANCELLED;
        finishedAt = occurredAt;
        this.note = normalizeNote(note);
    }

    private String normalizeNote(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getStepSequence() { return stepSequence; }
    public boolean isRequired() { return required; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
    public WorkStepStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getNote() { return note; }
}
