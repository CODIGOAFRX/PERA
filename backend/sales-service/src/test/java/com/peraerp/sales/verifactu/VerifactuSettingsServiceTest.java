package com.peraerp.sales.verifactu;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.verifactu.api.VerifactuSettingsRequest;
import com.peraerp.sales.verifactu.api.VerifactuSettingsResponse;
import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuMode;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Configuración de Veri*Factu por empresa.
 *
 * <p>Activar esto significa empezar a mandar registros a Hacienda, así que las comprobaciones
 * previas importan más que la comodidad: sin NIF del productor del software no se activa, y la
 * modalidad no implementada se rechaza en vez de fallar más adelante.</p>
 */
class VerifactuSettingsServiceTest {

    private static final UUID COMPANY = UUID.randomUUID();

    private VerifactuSettingsRepository repository;
    private CurrentCompanyProvider companyProvider;

    @BeforeEach
    void setUp() {
        repository = mock(VerifactuSettingsRepository.class);
        companyProvider = mock(CurrentCompanyProvider.class);
        when(companyProvider.requireCompanyId()).thenReturn(COMPANY);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private VerifactuSettingsService service(String developerTaxId) {
        return new VerifactuSettingsService(repository, companyProvider,
                "PERA ERP", "01", "0.1.0", developerTaxId);
    }

    private VerifactuSettingsRequest request(boolean enabled, VerifactuMode mode, String timeZone) {
        return new VerifactuSettingsRequest(enabled, mode, VerifactuEnvironment.TEST,
                "B75777847", "VIDRIOSERVICE S.L.", "01", "S1", timeZone);
    }

    // --- lectura ---

    @Test
    void unconfiguredCompanyGetsAProposalWithoutPersistingAnything() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        VerifactuSettingsResponse response = service("B00000000").current();

        assertThat(response.configured()).isFalse();
        assertThat(response.enabled()).isFalse();
        assertThat(response.environment()).isEqualTo(VerifactuEnvironment.TEST);
        assertThat(response.timeZone()).isEqualTo("Europe/Madrid");
        verify(repository, never()).save(any());
    }

    @Test
    void proposalCarriesTheSoftwareIdentityFromDeployment() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        VerifactuSettingsResponse response = service("B00000000").current();

        assertThat(response.softwareName()).isEqualTo("PERA ERP");
        assertThat(response.softwareVersion()).isEqualTo("0.1.0");
        assertThat(response.developerTaxId()).isEqualTo("B00000000");
    }

    @Test
    void softwareIdentityAlwaysComesFromTheDeploymentNotFromTheStoredRow() {
        VerifactuSettings stored = new VerifactuSettings(COMPANY, "89890001K", "EMPRESA DE PRUEBAS S.L.",
                "PERA ERP", "01", "0.0.1", "B99999999");
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.of(stored));

        VerifactuSettingsResponse response = service("B00000000").current();

        assertThat(response.developerTaxId()).isEqualTo("B00000000");
        assertThat(response.softwareVersion()).isEqualTo("0.1.0");
    }

    @Test
    void savingRefreshesTheStoredSoftwareIdentity() {
        VerifactuSettings stored = new VerifactuSettings(COMPANY, "89890001K", "EMPRESA DE PRUEBAS S.L.",
                "PERA ERP", "01", "0.0.1", "B99999999");
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.of(stored));

        service("B00000000").update(request(false, VerifactuMode.VERIFACTU, "Europe/Madrid"));

        assertThat(stored.getDeveloperTaxId()).isEqualTo("B00000000");
        assertThat(stored.getSoftwareVersion()).isEqualTo("0.1.0");
    }

    @Test
    void savedSettingsAreReturnedAsConfigured() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.of(
                new VerifactuSettings(COMPANY, "B75777847", "VIDRIOSERVICE S.L.",
                        "PERA ERP", "01", "0.1.0", "B00000000")));

        VerifactuSettingsResponse response = service("B00000000").current();

        assertThat(response.configured()).isTrue();
        assertThat(response.issuerTaxId()).isEqualTo("B75777847");
    }

    // --- guardado ---

    @Test
    void savingNormalisesTheIssuerTaxId() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        VerifactuSettingsResponse response = service("B00000000").update(new VerifactuSettingsRequest(
                false, VerifactuMode.VERIFACTU, VerifactuEnvironment.TEST, "  b75777847 ",
                "  VIDRIOSERVICE S.L.  ", "01", "S1", "Europe/Madrid"));

        assertThat(response.issuerTaxId()).isEqualTo("B75777847");
        assertThat(response.issuerLegalName()).isEqualTo("VIDRIOSERVICE S.L.");
        assertThat(response.configured()).isTrue();
    }

    @Test
    void environmentDecidesTheQrVerificationUrl() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        VerifactuSettingsResponse test = service("B00000000").update(request(false, VerifactuMode.VERIFACTU, "Europe/Madrid"));
        assertThat(test.qrValidationUrl()).startsWith("https://prewww2.aeat.es/");

        VerifactuSettingsResponse production = service("B00000000").update(new VerifactuSettingsRequest(
                false, VerifactuMode.VERIFACTU, VerifactuEnvironment.PRODUCTION, "B75777847",
                "VIDRIOSERVICE S.L.", "01", "S1", "Europe/Madrid"));
        assertThat(production.qrValidationUrl()).startsWith("https://www2.agenciatributaria.es/");
    }

    // --- comprobaciones antes de activar ---

    @Test
    void cannotEnableWithoutTheSoftwareProducerTaxId() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("").update(request(true, VerifactuMode.VERIFACTU, "Europe/Madrid")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("productor del software");
    }

    @Test
    void canSaveDisabledWithoutTheSoftwareProducerTaxId() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        VerifactuSettingsResponse response = service("").update(request(false, VerifactuMode.VERIFACTU, "Europe/Madrid"));

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void unimplementedModeIsRejectedUpFront() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("B00000000").update(request(false, VerifactuMode.NO_VERIFACTU, "Europe/Madrid")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("NO VERI*FACTU");
    }

    @Test
    void invalidTimeZoneIsRejected() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("B00000000").update(request(false, VerifactuMode.VERIFACTU, "Europa/Madrid")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zona horaria");
    }

    // --- uso desde la emisión ---

    @Test
    void issuingRequiresConfiguredSettings() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("B00000000").requireEnabled(COMPANY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no tiene configurado");
    }

    @Test
    void issuingRequiresVerifactuToBeEnabled() {
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.of(
                new VerifactuSettings(COMPANY, "B75777847", "VIDRIOSERVICE S.L.",
                        "PERA ERP", "01", "0.1.0", "B00000000")));

        assertThatThrownBy(() -> service("B00000000").requireEnabled(COMPANY))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("desactivado");
    }

    @Test
    void enabledSettingsAreReturnedForIssuing() {
        VerifactuSettings settings = new VerifactuSettings(COMPANY, "B75777847", "VIDRIOSERVICE S.L.",
                "PERA ERP", "01", "0.1.0", "B00000000");
        settings.configure(true, VerifactuMode.VERIFACTU, VerifactuEnvironment.TEST, "B75777847",
                "VIDRIOSERVICE S.L.", "01", "S1", "Europe/Madrid");
        when(repository.findByCompanyId(COMPANY)).thenReturn(Optional.of(settings));

        assertThat(service("B00000000").requireEnabled(COMPANY).getIssuerTaxId()).isEqualTo("B75777847");
        assertThat(settings.zone().getId()).isEqualTo("Europe/Madrid");
    }
}
