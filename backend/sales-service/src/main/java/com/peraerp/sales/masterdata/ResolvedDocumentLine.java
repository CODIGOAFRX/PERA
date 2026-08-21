package com.peraerp.sales.masterdata;

import java.math.BigDecimal;
import java.util.UUID;

public record ResolvedDocumentLine(UUID productId, String productCode, String description,
                                   BigDecimal requestedQuantity, BigDecimal billedQuantity,
                                   BigDecimal displayUnitPrice, BigDecimal discountPercentage,
                                   BigDecimal taxPercentage, UUID tariffId, String tariffCode,
                                   BigDecimal pricingResolvedAmount, String pricingTraceJson,
                                   UUID taxCodeId, String taxCode, String taxCountryCode,
                                   String taxName, Boolean taxExempt,
                                   com.peraerp.sales.verifactu.domain.OperationQualification taxQualification,
                                   com.peraerp.sales.verifactu.domain.ExemptionCause taxExemptionCause,
                                   String taxRegimeKey) {
    public ResolvedDocumentLine(UUID productId, String productCode, String description,
                                BigDecimal requestedQuantity, BigDecimal billedQuantity,
                                BigDecimal displayUnitPrice, BigDecimal discountPercentage,
                                BigDecimal taxPercentage, UUID tariffId, String tariffCode,
                                BigDecimal pricingResolvedAmount, String pricingTraceJson) {
        this(productId, productCode, description, requestedQuantity, billedQuantity, displayUnitPrice,
                discountPercentage, taxPercentage, tariffId, tariffCode, pricingResolvedAmount, pricingTraceJson,
                null, null, null, null, null, null, null, null);
    }

    /** Constructor de compatibilidad anterior a la calificación fiscal. */
    public ResolvedDocumentLine(UUID productId, String productCode, String description,
                                BigDecimal requestedQuantity, BigDecimal billedQuantity,
                                BigDecimal displayUnitPrice, BigDecimal discountPercentage,
                                BigDecimal taxPercentage, UUID tariffId, String tariffCode,
                                BigDecimal pricingResolvedAmount, String pricingTraceJson,
                                UUID taxCodeId, String taxCode, String taxCountryCode,
                                String taxName, Boolean taxExempt) {
        this(productId, productCode, description, requestedQuantity, billedQuantity, displayUnitPrice,
                discountPercentage, taxPercentage, tariffId, tariffCode, pricingResolvedAmount, pricingTraceJson,
                taxCodeId, taxCode, taxCountryCode, taxName, taxExempt, null, null, null);
    }
}
