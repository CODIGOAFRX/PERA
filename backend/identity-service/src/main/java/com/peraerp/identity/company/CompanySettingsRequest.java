package com.peraerp.identity.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanySettingsRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$") String countryCode,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2,3}(?:-[A-Za-z]{2}|-[0-9]{3})?$") @Size(max = 35) String locale,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String baseCurrency,
        @NotBlank @Size(max = 180) String displayName,
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._/-]{0,499}$") String logoStorageKey,
        @Pattern(regexp = "^image/(png|jpeg|webp)$") String logoContentType,
        @Pattern(regexp = "^[A-Fa-f0-9]{64}$") String logoSha256,
        @Email @Size(max = 180) String contactEmail,
        @Email @Size(max = 180) String invoiceEmail,
        @Email @Size(max = 180) String replyToEmail,
        @Size(max = 40) String phone,
        @Pattern(regexp = "^https?://[^\\s]+$") @Size(max = 240) String website,
        @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String city,
        @Size(max = 100) String region,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Razón social. Si se omite, se conserva la guardada.")
        @Size(max = 180) String legalName,
        @io.swagger.v3.oas.annotations.media.Schema(description = "NIF de la empresa. Si se omite, se conserva el guardado.")
        @Size(max = 30) String taxId
) {
    /** Clientes anteriores, que no envían razón social ni NIF: se conservan los guardados. */
    public CompanySettingsRequest(String countryCode, String locale, String timezone, String baseCurrency,
                                  String displayName, String logoStorageKey, String logoContentType,
                                  String logoSha256, String contactEmail, String invoiceEmail, String replyToEmail,
                                  String phone, String website, String addressLine1, String addressLine2,
                                  String postalCode, String city, String region) {
        this(countryCode, locale, timezone, baseCurrency, displayName, logoStorageKey, logoContentType, logoSha256,
                contactEmail, invoiceEmail, replyToEmail, phone, website, addressLine1, addressLine2, postalCode,
                city, region, null, null);
    }
}
