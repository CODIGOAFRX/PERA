package com.peraerp.sales.accounting;

import com.peraerp.sales.document.CommercialDocument;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingInvoiceSnapshot(UUID id, String number, String type, String status, LocalDate issueDate,
                                        String customerCode, String customerName, String baseCurrency,
                                        BigDecimal baseNetAmount, BigDecimal baseTaxAmount, BigDecimal baseTotalAmount) {
    static AccountingInvoiceSnapshot from(CommercialDocument document) {
        return new AccountingInvoiceSnapshot(document.getId(), document.getDocumentNumber(), document.getType().name(),
                document.getStatus().name(), document.getIssueDate(), document.getCustomerCodeSnapshot(),
                document.getCustomerNameSnapshot(), document.getBaseCurrency(), document.getBaseNetAmount(),
                document.getBaseTaxAmount(), document.getBaseTotalAmount());
    }
}
