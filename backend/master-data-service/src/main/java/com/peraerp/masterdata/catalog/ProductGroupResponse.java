package com.peraerp.masterdata.catalog;

import java.util.UUID;

public record ProductGroupResponse(UUID id, String code, String name, UUID productTypeId, boolean active) {
    static ProductGroupResponse from(ProductGroup group) {
        return new ProductGroupResponse(group.getId(), group.getCode(), group.getName(), group.getProductTypeId(),
                group.isActive());
    }
}
