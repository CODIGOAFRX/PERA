package com.peraerp.sales.verifactu.api;

import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuMode;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;

/**
 * Configuración de Veri*Factu de la empresa activa.
 *
 * @param configured {@code false} mientras la empresa no haya guardado nunca la configuración; el
 *                   resto de campos son entonces valores propuestos, no valores guardados
 */
public record VerifactuSettingsResponse(
        boolean configured,
        boolean enabled,
        VerifactuMode mode,
        VerifactuEnvironment environment,
        String issuerTaxId,
        String issuerLegalName,
        String defaultRegimeKey,
        String defaultOperationQualification,
        String timeZone,
        String qrValidationUrl,
        String softwareName,
        String softwareId,
        String softwareVersion,
        String developerTaxId) {

    /**
     * La identidad del productor se toma del despliegue, no de la fila guardada: la configuración
     * debe reflejar la versión del software que va a generar los próximos registros.
     */
    public static VerifactuSettingsResponse from(VerifactuSettings settings, String softwareName,
                                                 String softwareId, String softwareVersion,
                                                 String developerTaxId) {
        return new VerifactuSettingsResponse(true, settings.isEnabled(), settings.getMode(),
                settings.getEnvironment(), settings.getIssuerTaxId(), settings.getIssuerLegalName(),
                settings.getDefaultRegimeKey(), settings.getDefaultOperationQualification(),
                settings.getTimeZone(), settings.getEnvironment().qrValidationUrl(),
                softwareName, softwareId, softwareVersion, developerTaxId);
    }
}
