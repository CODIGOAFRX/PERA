package com.peraerp.sales.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(UUID id, String number, DocumentType type, DocumentStatus status, UUID customerId,
                               String customerCode, String customerName, LocalDate issueDate, LocalDate dueDate,
                               String currency, UUID sourceDocumentId, UUID paymentMethodId, PaymentStatus paymentStatus,
                               BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount, String notes,
                               List<DocumentLineResponse> lines) {
    public static DocumentResponse from(CommercialDocument document) {
        return new DocumentResponse(document.getId(), document.getDocumentNumber(), document.getType(),
                document.getStatus(), document.getCustomerId(), document.getCustomerCodeSnapshot(),
                document.getCustomerNameSnapshot(), document.getIssueDate(), document.getDueDate(), document.getCurrency(),
                document.getSourceDocumentId(), document.getPaymentMethodId(), document.getPaymentStatus(),
                document.getNetAmount(), document.getTaxAmount(), document.getTotalAmount(), document.getNotes(),
                document.getLines().stream().map(DocumentLineResponse::from).toList());
    }
}
