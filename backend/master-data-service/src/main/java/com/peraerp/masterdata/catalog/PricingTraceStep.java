package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.util.UUID;

public record PricingTraceStep(int order, String operation, UUID sourceId, String sourceCode,
                               String description, BigDecimal before, BigDecimal after) {}
