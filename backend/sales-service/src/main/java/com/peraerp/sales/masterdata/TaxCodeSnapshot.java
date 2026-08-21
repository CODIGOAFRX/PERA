package com.peraerp.sales.masterdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxCodeSnapshot(UUID id, String countryCode, String code, String name, BigDecimal percentage,
                              LocalDate validFrom, LocalDate validUntil, boolean exempt,
                              com.peraerp.sales.verifactu.domain.OperationQualification operationQualification,
                              com.peraerp.sales.verifactu.domain.ExemptionCause exemptionCause,
                              String regimeKey, boolean active) {

    /** Constructor de compatibilidad para los usos que no necesitan la calificación fiscal. */
    public TaxCodeSnapshot(UUID id, String countryCode, String code, String name, BigDecimal percentage,
                           LocalDate validFrom, LocalDate validUntil, boolean exempt, boolean active) {
        this(id, countryCode, code, name, percentage, validFrom, validUntil, exempt, null, null, null, active);
    }
    public boolean isApplicableOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }
}
