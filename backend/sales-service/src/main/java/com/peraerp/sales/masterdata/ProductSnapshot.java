package com.peraerp.sales.masterdata;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(UUID id, String code, String name, BigDecimal basePrice, BigDecimal taxRate,
                              boolean active, UUID taxCodeId) {
    public ProductSnapshot(UUID id, String code, String name, BigDecimal basePrice, BigDecimal taxRate,
                           boolean active) {
        this(id, code, name, basePrice, taxRate, active, null);
    }
}
