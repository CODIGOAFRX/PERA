package com.peraerp.sales.document;
import java.math.BigDecimal;
import java.util.UUID;

public record DocumentLineResponse(UUID id, int order, UUID productId, String productCode, String description,
                                   BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercentage,
                                   BigDecimal taxPercentage, BigDecimal netAmount, BigDecimal taxAmount,
                                   BigDecimal totalAmount, BigDecimal requestedQuantity, UUID tariffId,
                                   String tariffCode, BigDecimal pricingResolvedAmount, String pricingTraceJson,
                                   UUID taxCodeId, String taxCode, String taxCountryCode, String taxName,
                                   Boolean taxExempt) {
    static DocumentLineResponse from(DocumentLine line) {
        return new DocumentLineResponse(line.getId(), line.getLineOrder(), line.getProductId(),
                line.getProductCodeSnapshot(), line.getDescription(), line.getQuantity(), line.getUnitPrice(),
                line.getDiscountPercentage(), line.getTaxPercentage(), line.getNetAmount(), line.getTaxAmount(),
                line.getTotalAmount(), line.getRequestedQuantity(), line.getTariffId(),
                line.getTariffCodeSnapshot(), line.getPricingResolvedAmount(), line.getPricingTraceJson(),
                line.getTaxCodeId(), line.getTaxCodeSnapshot(), line.getTaxCountryCodeSnapshot(),
                line.getTaxNameSnapshot(), line.getTaxExemptSnapshot());
    }
}
