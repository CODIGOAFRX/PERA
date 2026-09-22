package com.peraerp.masterdata.supplier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SupplierRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 180) String legalName,
        @Size(max = 180) String tradeName,
        String taxId,
        @Size(max = 40) String phone,
        @Size(max = 180) String email,
        String observations,
        @Size(max = 160) String carrier,
        @Size(max = 160) String route,
        UUID defaultPaymentMethodId,
        Boolean active,
        @jakarta.validation.Valid com.peraerp.platform.domain.ContactDetails details
) {
    public SupplierRequest(String code, String legalName, String tradeName, String taxId, String phone, String email, String observations, String carrier, String route, UUID defaultPaymentMethodId, Boolean active) {
        this(code, legalName, tradeName, taxId, phone, email, observations, carrier, route, defaultPaymentMethodId, active, null);
    }
}
