package com.peraerp.sales.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateQuoteRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(max = 60) String customerCode,
        @NotBlank @Size(max = 180) String customerName,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate validUntil,
        @Size(min = 3, max = 3) String currency,
        UUID paymentMethodId,
        @Size(max = 4000) String notes,
        boolean sendOnCreate,
        @NotEmpty List<@Valid DocumentLineRequest> lines,
        UUID numberingSchemeId
) {
    CreateDocumentRequest toDocumentRequest() {
        return new CreateDocumentRequest(DocumentType.QUOTE, customerId, customerCode, customerName, issueDate,
                null, currency, paymentMethodId, notes, false, lines, numberingSchemeId);
    }
}
