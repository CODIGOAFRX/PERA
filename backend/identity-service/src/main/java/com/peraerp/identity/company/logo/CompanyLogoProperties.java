package com.peraerp.identity.company.logo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pera.company-logo")
public record CompanyLogoProperties(String storageRoot) {

    public CompanyLogoProperties {
        if (storageRoot == null || storageRoot.isBlank()) {
            throw new IllegalArgumentException("Company logo storage root must be configured.");
        }
    }
}
