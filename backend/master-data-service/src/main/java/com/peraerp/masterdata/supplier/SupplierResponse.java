package com.peraerp.masterdata.supplier;

import com.peraerp.masterdata.party.Party;
import java.util.UUID;

public record SupplierResponse(UUID id, UUID partyId, String code, String legalName, String tradeName,
                               String taxId, String phone, String email, boolean active,
                               String carrier, String route, UUID defaultPaymentMethodId) {
    static SupplierResponse from(SupplierProfile profile, Party party) {
        return new SupplierResponse(profile.getId(), party.getId(), party.getCode(), party.getLegalName(),
                party.getTradeName(), party.getTaxId(), party.getPhone(), party.getEmail(), party.isActive(),
                profile.getCarrier(), profile.getRoute(), profile.getDefaultPaymentMethodId());
    }
}
