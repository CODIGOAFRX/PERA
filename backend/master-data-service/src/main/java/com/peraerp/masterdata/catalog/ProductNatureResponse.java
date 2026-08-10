package com.peraerp.masterdata.catalog;

import java.util.UUID;

public record ProductNatureResponse(UUID id, String code, String name, boolean active) {
    static ProductNatureResponse from(ProductNature nature) {
        return new ProductNatureResponse(nature.getId(), nature.getCode(), nature.getName(), nature.isActive());
    }
}
