package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.party.Party;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerResponse(
        UUID id, UUID partyId, String code, String legalName, String tradeName, String taxId,
        String phone, String email, String observations, boolean active, UUID priceListId,
        UUID defaultPaymentMethodId, String supplierCode, BigDecimal calculationMultiplier,
        BigDecimal creditLimit, BigDecimal riskWarningThreshold, RiskPolicy riskPolicy
) {
    static CustomerResponse from(CustomerProfile profile, Party party) {
        return new CustomerResponse(profile.getId(), party.getId(), party.getCode(), party.getLegalName(),
                party.getTradeName(), party.getTaxId(), party.getPhone(), party.getEmail(), party.getObservations(),
                party.isActive(), profile.getPriceListId(), profile.getDefaultPaymentMethodId(),
                profile.getSupplierCode(), profile.getCalculationMultiplier(), profile.getCreditLimit(),
                profile.getRiskWarningThreshold(), profile.getRiskPolicy());
    }
}
