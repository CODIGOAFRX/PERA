package com.peraerp.masterdata.customer;

import com.peraerp.masterdata.party.TaxIdentificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 180) String legalName,
        @Size(max = 180) String tradeName,
        @Size(max = 30) String taxId,
        @Schema(description = "NIF para residentes en España. Para el resto, el tipo de documento del bloque IDOtro de Veri*Factu. Si se omite y hay identificador, se asume NIF.")
        TaxIdentificationType taxIdentificationType,
        @Schema(description = "País de expedición del identificador fiscal, ISO 3166-1 alfa-2.")
        @Size(min = 2, max = 2) String taxCountryCode,
        @Size(max = 40) String phone,
        @Size(max = 180) String email,
        String observations,
        UUID priceListId,
        UUID defaultPaymentMethodId,
        @Size(max = 60) String supplierCode,
        @Schema(description = "Campo heredado congelado; no interviene en el cálculo horizontal de precios.", deprecated = true)
        @Deprecated(since = "0.2", forRemoval = false)
        @DecimalMin("0.000001") BigDecimal calculationMultiplier,
        @DecimalMin("0") BigDecimal creditLimit,
        @DecimalMin("0") BigDecimal riskWarningThreshold,
        RiskPolicy riskPolicy,
        Boolean active
) {}
