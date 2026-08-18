package com.peraerp.operations.workflow;

import com.peraerp.platform.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "workflow_template_steps", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workflow_step_code", columnNames = {"template_id", "code"}),
        @UniqueConstraint(name = "uk_workflow_step_sequence", columnNames = {"template_id", "step_sequence"})
})
public class WorkflowStepDefinition extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private WorkflowTemplate template;
    @Column(nullable = false, length = 60)
    private String code;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "step_sequence", nullable = false)
    private int stepSequence;
    @Column(name = "required_step", nullable = false)
    private boolean required;
    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    protected WorkflowStepDefinition() {
    }

    public WorkflowStepDefinition(String code, String name, String description, int stepSequence,
                                  boolean required, Integer estimatedMinutes) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.stepSequence = stepSequence;
        this.required = required;
        this.estimatedMinutes = estimatedMinutes;
    }

    void attachTo(WorkflowTemplate template) {
        this.template = template;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getStepSequence() { return stepSequence; }
    public boolean isRequired() { return required; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
}
