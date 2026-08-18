package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PricingRuleResponse(UUID id, UUID tariffId, PricingTargetType targetType, UUID productNatureId,
                                  UUID productSupertypeId, UUID productTypeId, UUID productGroupId, UUID productId,
                                  UUID customerId, BigDecimal fixedPrice, BigDecimal discountPercentage,
                                  BigDecimal surchargePercentage, int priority, LocalDate validFrom,
                                  LocalDate validUntil, boolean active) {
    static PricingRuleResponse from(PricingRule rule) {
        return new PricingRuleResponse(rule.getId(), rule.getPriceListId(), rule.getTargetType(),
                rule.getProductNatureId(), rule.getProductSupertypeId(), rule.getProductTypeId(),
                rule.getProductGroupId(), rule.getProductId(), rule.getCustomerId(), rule.getFixedPrice(),
                rule.getDiscountPercentage(), rule.getSurchargePercentage(), rule.getPriority(),
                rule.getValidFrom(), rule.getValidUntil(), rule.isActive());
    }
}
