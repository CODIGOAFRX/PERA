package com.peraerp.masterdata.packaging;

import java.math.BigDecimal;
import java.util.UUID;

public record PackagingTypeResponse(UUID id, String code, String name, String description,
                                    BigDecimal internalLength, BigDecimal internalWidth,
                                    BigDecimal internalHeight, BigDecimal externalLength,
                                    BigDecimal externalWidth, BigDecimal externalHeight,
                                    BigDecimal tareWeight, BigDecimal maximumWeight,
                                    BigDecimal maximumVolume, boolean returnable, boolean active) {
    static PackagingTypeResponse from(PackagingType type) {
        return new PackagingTypeResponse(type.getId(), type.getCode(), type.getName(), type.getDescription(),
                type.getInternalLength(), type.getInternalWidth(), type.getInternalHeight(),
                type.getExternalLength(), type.getExternalWidth(), type.getExternalHeight(), type.getTareWeight(),
                type.getMaximumWeight(), type.getMaximumVolume(), type.isReturnable(), type.isActive());
    }
}
