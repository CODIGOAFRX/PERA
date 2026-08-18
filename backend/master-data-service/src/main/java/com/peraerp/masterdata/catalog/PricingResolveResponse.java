package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PricingResolveResponse(UUID tariffId, String tariffCode, String currency,
                                     BigDecimal requestedQuantity, BigDecimal billedQuantity,
                                     BigDecimal baseUnitPrice, BigDecimal finalUnitPrice,
                                     BigDecimal subtotal, BigDecimal finalPrice,
                                     List<PricingTraceStep> trace) {}
