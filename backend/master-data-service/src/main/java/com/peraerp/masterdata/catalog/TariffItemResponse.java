package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TariffItemResponse(UUID id, UUID tariffId, UUID productId, UUID customerId, BigDecimal price,
                                 BigDecimal discountPercentage, BigDecimal surchargePercentage, int priority,
                                 LocalDate validFrom, LocalDate validUntil, boolean active) {
    static TariffItemResponse from(PriceListItem item) {
        return new TariffItemResponse(item.getId(), item.getPriceListId(), item.getProductId(), item.getCustomerId(),
                item.getPrice(), item.getDiscountPercentage(), item.getSurchargePercentage(), item.getPriority(),
                item.getValidFrom(), item.getValidUntil(), item.isActive());
    }
}
