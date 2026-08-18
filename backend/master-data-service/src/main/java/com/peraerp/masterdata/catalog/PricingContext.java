package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

record PricingContext(UUID companyId, UUID customerId, UUID assignedTariffId, UUID productId, UUID productNatureId,
                      UUID productSupertypeId, UUID productTypeId, UUID productGroupId,
                      BigDecimal quantity, LocalDate date, BigDecimal basePrice, String currency) {}
