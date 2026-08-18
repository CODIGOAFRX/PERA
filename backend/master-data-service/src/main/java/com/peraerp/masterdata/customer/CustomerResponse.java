package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.party.Party;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id, UUID partyId, String code, String legalName, String tradeName, String taxId,
        String phone, String email, String observations, boolean active, UUID priceListId,
        UUID defaultPaymentMethodId, String supplierCode,
        @Schema(description = "Campo heredado congelado; no interviene en el cálculo horizontal de precios.", deprecated = true)
        @Deprecated(since = "0.2", forRemoval = false) BigDecimal calculationMultiplier,
        BigDecimal creditLimit, BigDecimal riskWarningThreshold, RiskPolicy riskPolicy, Instant createdAt
) {
    @SuppressWarnings("deprecation") // Frontera de compatibilidad: el campo se devuelve, pero no se usa en reglas nuevas.
    static CustomerResponse from(CustomerProfile profile, Party party) {
        return new CustomerResponse(profile.getId(), party.getId(), party.getCode(), party.getLegalName(),
                party.getTradeName(), party.getTaxId(), party.getPhone(), party.getEmail(), party.getObservations(),
                party.isActive(), profile.getPriceListId(), profile.getDefaultPaymentMethodId(),
                profile.getSupplierCode(), profile.getCalculationMultiplier(), profile.getCreditLimit(),
                profile.getRiskWarningThreshold(), profile.getRiskPolicy(), profile.getCreatedAt());
    }
}
