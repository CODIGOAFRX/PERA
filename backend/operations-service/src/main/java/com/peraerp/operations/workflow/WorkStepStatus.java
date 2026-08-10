package com.peraerp.operations.workflow;

public enum WorkStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == SKIPPED || this == CANCELLED;
    }
}
