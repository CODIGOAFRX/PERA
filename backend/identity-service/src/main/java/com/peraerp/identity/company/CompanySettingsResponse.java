package com.peraerp.identity.company;

import java.time.Instant;
import java.util.UUID;

public record CompanySettingsResponse(
        UUID id,
        UUID companyId,
        String countryCode,
        String locale,
        String timezone,
        String baseCurrency,
        String displayName,
        String logoStorageKey,
        String logoContentType,
        String logoSha256,
        String contactEmail,
        String invoiceEmail,
        String replyToEmail,
        String phone,
        String website,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String city,
        String region,
        Instant updatedAt,
        long version
) {
    static CompanySettingsResponse from(CompanySettings settings) {
        return new CompanySettingsResponse(settings.getId(), settings.getCompanyId(), settings.getCountryCode(),
                settings.getLocale(), settings.getTimezone(), settings.getBaseCurrency(), settings.getDisplayName(),
                settings.getLogoStorageKey(), settings.getLogoContentType(), settings.getLogoSha256(),
                settings.getContactEmail(), settings.getInvoiceEmail(), settings.getReplyToEmail(), settings.getPhone(),
                settings.getWebsite(), settings.getAddressLine1(), settings.getAddressLine2(), settings.getPostalCode(),
                settings.getCity(), settings.getRegion(), settings.getUpdatedAt(), settings.getVersion());
    }
}
