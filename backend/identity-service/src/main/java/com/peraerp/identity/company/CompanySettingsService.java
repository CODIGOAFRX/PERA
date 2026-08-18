package com.peraerp.identity.company;

import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CompanySettingsService {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private final CompanySettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;
    private final CurrentCompanyProvider companyProvider;

    public CompanySettingsService(CompanySettingsRepository settingsRepository, CompanyRepository companyRepository,
                                  CurrentCompanyProvider companyProvider) {
        this.settingsRepository = settingsRepository;
        this.companyRepository = companyRepository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public CompanySettingsResponse findCurrent() {
        UUID companyId = companyProvider.requireCompanyId();
        return CompanySettingsResponse.from(requireOrCreate(companyId));
    }

    @Transactional
    public CompanySettingsResponse updateCurrent(CompanySettingsRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        validateReferenceData(request);
        CompanySettings settings = requireOrCreate(companyId);
        validateCompatibleLogoMetadata(settings, request);
        settings.updateProfile(request.countryCode().trim().toUpperCase(Locale.ROOT), request.locale().trim(),
                request.timezone().trim(), request.baseCurrency().trim().toUpperCase(Locale.ROOT),
                request.displayName().trim(), nullable(request.contactEmail()), nullable(request.invoiceEmail()),
                nullable(request.replyToEmail()), nullable(request.phone()), nullable(request.website()),
                nullable(request.addressLine1()), nullable(request.addressLine2()), nullable(request.postalCode()),
                nullable(request.city()), nullable(request.region()));
        return CompanySettingsResponse.from(settings);
    }

    private CompanySettings requireOrCreate(UUID companyId) {
        return settingsRepository.findByCompanyId(companyId).orElseGet(() -> {
            Company company = companyRepository.findById(companyId)
                    .filter(Company::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Empresa", companyId));
            return settingsRepository.save(CompanySettings.defaults(companyId, company.getName()));
        });
    }

    private void validateReferenceData(CompanySettingsRequest request) {
        String countryCode = request.countryCode().trim().toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRIES.contains(countryCode)) {
            throw new BusinessRuleException("El código de país no pertenece a ISO 3166-1 alpha-2.");
        }
        try {
            ZoneId.of(request.timezone().trim());
        } catch (DateTimeException exception) {
            throw new BusinessRuleException("La zona horaria indicada no es válida.");
        }
        try {
            Currency.getInstance(request.baseCurrency().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("La moneda base no pertenece a ISO 4217.");
        }
    }

    private void validateCompatibleLogoMetadata(CompanySettings settings, CompanySettingsRequest request) {
        boolean hasKey = hasText(request.logoStorageKey());
        boolean hasContentType = hasText(request.logoContentType());
        boolean hasChecksum = hasText(request.logoSha256());
        if (hasKey != hasContentType || hasKey != hasChecksum) {
            throw new BusinessRuleException(
                    "La clave, el tipo de contenido y el checksum del logo deben informarse juntos.");
        }
        if (hasKey && (!request.logoStorageKey().trim().equals(settings.getLogoStorageKey())
                || !request.logoContentType().trim().equalsIgnoreCase(settings.getLogoContentType())
                || !request.logoSha256().trim().equalsIgnoreCase(settings.getLogoSha256()))) {
            throw new BusinessRuleException(
                    "El logo solo se puede modificar mediante el endpoint de carga de archivos.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
