package com.peraerp.identity.company;

import java.util.UUID;

public record CompanyResponse(UUID id, String code, String name, String taxId, boolean active) {
    static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getId(), company.getCode(), company.getName(), company.getTaxId(), company.isActive());
    }
}
