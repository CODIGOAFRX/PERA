package com.peraerp.sales.document;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record DocumentLineRequest(
        UUID productId,
        @Size(max = 60) String productCode,
        @NotBlank @Size(max = 300) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin("0") BigDecimal unitPrice,
        @DecimalMin("0") BigDecimal discountPercentage,
        @DecimalMin("0") BigDecimal taxPercentage
) {}
