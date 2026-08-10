package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TariffResponse(
        UUID id,
        String code,
        String name,
        String currency,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active,
        int priority,
        PricingScope scope,
        UUID customerId,
        UUID productNatureId,
        UUID productSupertypeId,
        UUID productTypeId,
        UUID productGroupId,
        UUID productId,
        UUID parentTariffId,
        BigDecimal generalSurchargePercentage,
        BigDecimal energySurchargePercentage,
        BigDecimal minimumBillingAmount,
        BigDecimal unitMultiple,
        BigDecimal minimumPerPiece
) {
    static TariffResponse from(PriceList tariff) {
        return new TariffResponse(tariff.getId(), tariff.getCode(), tariff.getName(), tariff.getCurrency(),
                tariff.getValidFrom(), tariff.getValidUntil(), tariff.isActive(), tariff.getPriority(),
                tariff.getScope(), tariff.getCustomerId(), tariff.getProductNatureId(),
                tariff.getProductSupertypeId(), tariff.getProductTypeId(), tariff.getProductGroupId(),
                tariff.getProductId(), tariff.getParentPriceListId(), tariff.getGeneralSurchargePercentage(),
                tariff.getEnergySurchargePercentage(), tariff.getMinimumBillingAmount(), tariff.getUnitMultiple(),
                tariff.getMinimumPerPiece());
    }
}
