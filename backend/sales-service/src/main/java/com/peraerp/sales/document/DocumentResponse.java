package com.peraerp.sales.document;

import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.RectificationType;
import com.peraerp.sales.verifactu.domain.TaxIdentificationType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(UUID id, String number, DocumentType type, DocumentStatus status, UUID customerId,
                               String customerCode, String customerName, LocalDate issueDate, LocalDate dueDate,
                               String currency, UUID sourceDocumentId, UUID paymentMethodId, PaymentStatus paymentStatus,
                               BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                               String baseCurrency, BigDecimal exchangeRate, LocalDate exchangeRateDate,
                               String exchangeRateSource, BigDecimal baseNetAmount, BigDecimal baseTaxAmount,
                               BigDecimal baseTotalAmount, String notes,
                               List<DocumentLineResponse> lines,
                               QuoteStatus quoteStatus, LocalDate quoteValidUntil, Instant quoteDecidedAt,
                               String quoteRejectionReason,
                               InvoiceKind invoiceKind, RectificationType rectificationType,
                               UUID rectifiedDocumentId, String rectifiedNumber, LocalDate rectifiedIssueDate,
                               boolean issued,
                               String customerTaxId, TaxIdentificationType customerTaxIdentificationType,
                               String customerTaxCountry) {
    public static DocumentResponse from(CommercialDocument document) {
        return new DocumentResponse(document.getId(), document.getDocumentNumber(), document.getType(),
                document.getStatus(), document.getCustomerId(), document.getCustomerCodeSnapshot(),
                document.getCustomerNameSnapshot(), document.getIssueDate(), document.getDueDate(), document.getCurrency(),
                document.getSourceDocumentId(), document.getPaymentMethodId(), document.getPaymentStatus(),
                document.getNetAmount(), document.getTaxAmount(), document.getTotalAmount(),
                document.getBaseCurrency(), document.getExchangeRate(), document.getExchangeRateDate(),
                document.getExchangeRateSource(), document.getBaseNetAmount(), document.getBaseTaxAmount(),
                document.getBaseTotalAmount(), document.getNotes(),
                document.getLines().stream().map(DocumentLineResponse::from).toList(),
                document.getQuoteStatus(), document.getQuoteValidUntil(), document.getQuoteDecidedAt(),
                document.getQuoteRejectionReason(),
                document.getInvoiceKind(), document.getRectificationType(), document.getRectifiedDocumentId(),
                document.getRectifiedNumberSnapshot(), document.getRectifiedIssueDateSnapshot(),
                document.isIssued(),
                document.getCustomerTaxIdSnapshot(), document.getCustomerTaxIdentificationTypeSnapshot(),
                document.getCustomerTaxCountrySnapshot());
    }
}
