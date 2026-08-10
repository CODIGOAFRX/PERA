package com.peraerp.sales.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectQuoteRequest(@NotBlank @Size(max = 500) String reason) {
}
