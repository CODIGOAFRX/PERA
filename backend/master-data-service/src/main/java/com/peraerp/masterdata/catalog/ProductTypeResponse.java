package com.peraerp.masterdata.catalog;

import java.util.UUID;

public record ProductTypeResponse(UUID id, String code, String name, UUID supertypeId, boolean active) {
    static ProductTypeResponse from(ProductType type) {
        return new ProductTypeResponse(type.getId(), type.getCode(), type.getName(), type.getSupertypeId(),
                type.isActive());
    }
}
