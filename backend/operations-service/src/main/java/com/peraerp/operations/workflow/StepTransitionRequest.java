package com.peraerp.operations.workflow;

import jakarta.validation.constraints.Size;

public record StepTransitionRequest(@Size(max = 2000) String note) {
}
