package com.peraerp.sales.masterdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxCodeSnapshot(UUID id, String countryCode, String code, String name, BigDecimal percentage,
                              LocalDate validFrom, LocalDate validUntil, boolean exempt, boolean active) {
    public boolean isApplicableOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }
}
