package com.peraerp.masterdata.catalog;

import java.util.UUID;

public record ProductSupertypeResponse(UUID id, String code, String name, UUID natureId, boolean active) {
    static ProductSupertypeResponse from(ProductSupertype supertype) {
        return new ProductSupertypeResponse(supertype.getId(), supertype.getCode(), supertype.getName(),
                supertype.getNatureId(), supertype.isActive());
    }
}
