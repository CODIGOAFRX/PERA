package com.peraerp.masterdata.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(UUID id, String code, String name, String description, UUID productTypeId,
                              UUID productGroupId, UUID taxCodeId, UUID familyId, UUID categoryId,
                              UnitOfMeasure unitOfMeasure, BigDecimal basePrice, BigDecimal taxRate, boolean active,
                              Instant createdAt) {
    static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getCode(), product.getName(), product.getDescription(),
                product.getProductTypeId(), product.getProductGroupId(), product.getTaxCodeId(),
                product.getFamilyId(), product.getCategoryId(), product.getUnitOfMeasure(), product.getBasePrice(),
                product.getTaxRate(), product.isActive(), product.getCreatedAt());
    }
}
