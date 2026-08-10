package com.peraerp.sales.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateDocumentRequest(
        @NotNull DocumentType type,
        @NotNull UUID customerId,
        @NotBlank @Size(max = 60) String customerCode,
        @NotBlank @Size(max = 180) String customerName,
        @NotNull LocalDate issueDate,
        LocalDate dueDate,
        @Size(min = 3, max = 3) String currency,
        UUID paymentMethodId,
        String notes,
        boolean confirm,
        @NotEmpty List<@Valid DocumentLineRequest> lines,
        UUID numberingSchemeId
) {
    public CreateDocumentRequest(DocumentType type, UUID customerId, String customerCode, String customerName,
                                 LocalDate issueDate, LocalDate dueDate, String currency, UUID paymentMethodId,
                                 String notes, boolean confirm, List<DocumentLineRequest> lines) {
        this(type, customerId, customerCode, customerName, issueDate, dueDate, currency, paymentMethodId,
                notes, confirm, lines, null);
    }
}
