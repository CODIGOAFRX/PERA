package com.peraerp.sales.masterdata;

import java.math.BigDecimal;
import java.util.UUID;

public record PricingTraceSnapshot(int order, String operation, UUID sourceId, String sourceCode,
                                   String description, BigDecimal before, BigDecimal after) {
}
