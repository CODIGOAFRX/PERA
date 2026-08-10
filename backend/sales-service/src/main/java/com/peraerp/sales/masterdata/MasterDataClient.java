package com.peraerp.sales.masterdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface MasterDataClient {
    CustomerSnapshot findCustomer(UUID customerId);
    ProductSnapshot findProduct(UUID productId);
    TaxCodeSnapshot findTaxCode(UUID taxCodeId);
    PricingSnapshot resolvePrice(UUID customerId, UUID productId, BigDecimal quantity,
                                 LocalDate date, BigDecimal basePrice, String currency);
}
