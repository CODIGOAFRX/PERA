package com.peraerp.identity.company;

import com.peraerp.identity.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySettingsServiceTest {

    @Mock CompanySettingsRepository settingsRepository;
    @Mock CompanyRepository companyRepository;
    @Mock CurrentCompanyProvider companyProvider;

    private CompanySettingsService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service = new CompanySettingsService(settingsRepository, companyRepository, companyProvider);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsSafeDefaultsForTheSignedTenantWhenSettingsAreMissing() {
        Company company = mock(Company.class);
        when(company.getName()).thenReturn("PERA Demo");
        when(company.isActive()).thenReturn(true);
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.empty());
        when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
        when(settingsRepository.save(any(CompanySettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompanySettingsResponse response = service.findCurrent();

        assertThat(response.companyId()).isEqualTo(companyId);
        assertThat(response.countryCode()).isEqualTo("ES");
        assertThat(response.locale()).isEqualTo("es-ES");
        assertThat(response.timezone()).isEqualTo("Europe/Madrid");
        assertThat(response.baseCurrency()).isEqualTo("EUR");
        assertThat(response.displayName()).isEqualTo("PERA Demo");
        verify(settingsRepository).findByCompanyId(companyId);
    }

    @Test
    void updatesOnlySettingsResolvedFromTheSignedTenant() {
        CompanySettings settings = CompanySettings.defaults(companyId, "Old name");
        String storageKey = "companies/" + companyId + "/logos/pear.png";
        settings.updateLogo(storageKey, "image/png", "a".repeat(64));
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));

        CompanySettingsResponse response = service.updateCurrent(request(storageKey, "Europe/Madrid", "EUR"));

        assertThat(response.companyId()).isEqualTo(companyId);
        assertThat(response.displayName()).isEqualTo("PERA España");
        assertThat(response.logoStorageKey()).isEqualTo(storageKey);
        assertThat(response.logoSha256()).isEqualTo("a".repeat(64));
        verify(settingsRepository).findByCompanyId(companyId);
    }

    @Test
    void rejectsLogoMetadataMutationThroughTheProfileEndpoint() {
        UUID foreignCompanyId = UUID.randomUUID();
        CompanySettings settings = CompanySettings.defaults(companyId, "PERA");
        when(settingsRepository.findByCompanyId(companyId)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> service.updateCurrent(request(
                "companies/" + foreignCompanyId + "/logos/pear.png", "Europe/Madrid", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("endpoint de carga");
    }

    @Test
    void rejectsUnknownTimezoneAndCurrency() {
        assertThatThrownBy(() -> service.updateCurrent(request(null, "Mars/Olympus", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zona horaria");

        assertThatThrownBy(() -> service.updateCurrent(request(null, "Europe/Madrid", "ZZZ")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ISO 4217");
    }

    private CompanySettingsRequest request(String storageKey, String timezone, String currency) {
        return new CompanySettingsRequest("ES", "es-ES", timezone, currency, " PERA España ", storageKey,
                storageKey == null ? null : "image/png", storageKey == null ? null : "a".repeat(64),
                "contacto@example.com", "facturas@example.com", "respuesta@example.com", "+34 900 000 000",
                "https://pera.example", "Calle Pera 1", null, "28001", "Madrid", "Madrid");
    }
}
