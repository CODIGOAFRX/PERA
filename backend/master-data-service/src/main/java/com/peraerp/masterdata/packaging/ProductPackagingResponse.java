package com.peraerp.masterdata.packaging;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPackagingResponse(UUID id, UUID productId, UUID packagingTypeId, String code,
                                       BigDecimal unitsPerPackage, Integer levels, BigDecimal unitsPerLevel,
                                       BigDecimal length, BigDecimal width, BigDecimal height,
                                       BigDecimal grossWeight, boolean defaultPackaging, boolean active) {
    static ProductPackagingResponse from(ProductPackaging packaging) {
        return new ProductPackagingResponse(packaging.getId(), packaging.getProductId(),
                packaging.getPackagingTypeId(), packaging.getCode(), packaging.getUnitsPerPackage(),
                packaging.getLevels(), packaging.getUnitsPerLevel(), packaging.getLength(), packaging.getWidth(),
                packaging.getHeight(), packaging.getGrossWeight(), packaging.isDefaultPackaging(),
                packaging.isActive());
    }
}
