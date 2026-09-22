package com.peraerp.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/** Optional postal and banking data, owned by the containing tenant-scoped aggregate. */
@Embeddable
public record ContactDetails(
        @Size(max = 240) @Column(name = "contact_address", length = 240) String addressLine1,
        @Size(max = 120) @Column(name = "contact_city", length = 120) String city,
        @Size(max = 120) @Column(name = "contact_region", length = 120) String region,
        @Size(max = 20) @Column(name = "contact_postal_code", length = 20) String postalCode,
        @Size(max = 2) @Column(name = "contact_country", length = 2) String countryCode,
        @Size(max = 34) @Column(name = "bank_iban", length = 34) String iban,
        @Size(max = 180) @Column(name = "bank_holder", length = 180) String bankAccountHolder) {
    public ContactDetails {
        addressLine1 = clean(addressLine1); city = clean(city); region = clean(region);
        postalCode = clean(postalCode); countryCode = clean(countryCode);
        if (countryCode != null) {
            countryCode = countryCode.toUpperCase(Locale.ROOT);
            if (!countryCode.matches("[A-Z]{2}")) throw new BusinessRuleException("El país debe tener dos letras (por ejemplo ES).");
        }
        bankAccountHolder = clean(bankAccountHolder);
        iban = normalizeIban(iban);
    }
    public String postalAddress() {
        return Stream.of(addressLine1, postalCode, city, region, countryCode)
                .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(", "));
    }
    public static String normalizeIban(String value) {
        if (value == null || value.isBlank()) return null;
        String iban = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}") || (iban.startsWith("ES") && iban.length() != 24))
            throw new BusinessRuleException("El IBAN no tiene un formato válido.");
        String rotated = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (char c : rotated.toCharArray()) {
            int n = Character.isDigit(c) ? c - '0' : c - 'A' + 10;
            remainder = (remainder * (n < 10 ? 10 : 100) + n) % 97;
        }
        if (remainder != 1) throw new BusinessRuleException("El dígito de control del IBAN no es válido.");
        return iban;
    }
    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
