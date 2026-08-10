package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxCodeResponse(UUID id, String countryCode, String code, String name, BigDecimal percentage,
                              LocalDate validFrom, LocalDate validUntil, boolean exempt, boolean active) {
    static TaxCodeResponse from(TaxCode taxCode) {
        return new TaxCodeResponse(taxCode.getId(), taxCode.getCountryCode(), taxCode.getCode(), taxCode.getName(),
                taxCode.getPercentage(), taxCode.getValidFrom(), taxCode.getValidUntil(), taxCode.isExempt(),
                taxCode.isActive());
    }
}
