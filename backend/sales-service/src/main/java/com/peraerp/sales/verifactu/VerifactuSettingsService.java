package com.peraerp.sales.verifactu;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.verifactu.api.VerifactuSettingsRequest;
import com.peraerp.sales.verifactu.api.VerifactuSettingsResponse;
import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuMode;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * Configuración de Veri*Factu por empresa.
 *
 * <p>La identidad del <em>productor de software</em> (PERA) llega por configuración del despliegue
 * y se copia en cada fila al guardarla. Es información del fabricante, no del cliente, y por eso
 * no se puede editar desde la aplicación: quien responde ante la AEAT por el programa es quien lo
 * comercializa.</p>
 */
@Service
public class VerifactuSettingsService {

    private final VerifactuSettingsRepository repository;
    private final CurrentCompanyProvider companyProvider;
    private final String softwareName;
    private final String softwareId;
    private final String softwareVersion;
    private final String developerTaxId;

    public VerifactuSettingsService(VerifactuSettingsRepository repository,
                                    CurrentCompanyProvider companyProvider,
                                    @Value("${pera.verifactu.software.name:PERA ERP}") String softwareName,
                                    @Value("${pera.verifactu.software.id:01}") String softwareId,
                                    @Value("${pera.verifactu.software.version:0.1.0}") String softwareVersion,
                                    @Value("${pera.verifactu.software.developer-tax-id:}") String developerTaxId) {
        this.repository = repository;
        this.companyProvider = companyProvider;
        this.softwareName = softwareName;
        this.softwareId = softwareId;
        this.softwareVersion = softwareVersion;
        this.developerTaxId = developerTaxId;
    }

    /**
     * Configuración actual, o una propuesta sin guardar si la empresa nunca la ha configurado.
     *
     * <p>Una lectura no crea filas. La alternativa —persistir al primer GET— llenaría la tabla de
     * configuraciones vacías de empresas que nunca van a usar Veri*Factu.</p>
     */
    @Transactional(readOnly = true)
    public VerifactuSettingsResponse current() {
        UUID companyId = companyProvider.requireCompanyId();
        return repository.findByCompanyId(companyId)
                .map(settings -> VerifactuSettingsResponse.from(settings, softwareName, softwareId,
                        softwareVersion, developerTaxId))
                .orElseGet(this::proposal);
    }

    @Transactional
    public VerifactuSettingsResponse update(VerifactuSettingsRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        validate(request);

        VerifactuSettings settings = repository.findByCompanyId(companyId)
                .orElseGet(() -> new VerifactuSettings(companyId, request.issuerTaxId(),
                        request.issuerLegalName(), softwareName, softwareId, softwareVersion, developerTaxId));

        settings.configure(request.enabled(), request.mode(), request.environment(),
                request.issuerTaxId().trim().toUpperCase(Locale.ROOT), request.issuerLegalName().trim(),
                request.defaultRegimeKey(), request.defaultOperationQualification(), request.timeZone());
        settings.syncSoftwareIdentity(softwareName, softwareId, softwareVersion, developerTaxId);

        return VerifactuSettingsResponse.from(repository.save(settings), softwareName, softwareId,
                softwareVersion, developerTaxId);
    }

    /**
     * Configuración efectiva para emitir. Falla si la empresa no lo tiene activado, porque emitir
     * un registro con una configuración a medias es peor que no emitirlo.
     */
    @Transactional(readOnly = true)
    public VerifactuSettings requireEnabled(UUID companyId) {
        VerifactuSettings settings = repository.findByCompanyId(companyId)
                .orElseThrow(() -> new BusinessRuleException(
                        "La empresa no tiene configurado Veri*Factu."));
        if (!settings.isEnabled()) {
            throw new BusinessRuleException("La empresa tiene Veri*Factu desactivado.");
        }
        return settings;
    }

    private void validate(VerifactuSettingsRequest request) {
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException e) {
            throw new BusinessRuleException("La zona horaria «" + request.timeZone() + "» no es válida.");
        }
        if (request.enabled() && (developerTaxId == null || developerTaxId.isBlank())) {
            throw new BusinessRuleException(
                    "No se puede activar Veri*Factu sin el NIF del productor del software. "
                            + "Configura pera.verifactu.software.developer-tax-id en el despliegue.");
        }
        if (request.mode() == VerifactuMode.NO_VERIFACTU) {
            throw new BusinessRuleException(
                    "La modalidad NO VERI*FACTU todavía no está implementada: exige firma electrónica "
                            + "de cada registro y registro de eventos.");
        }
    }

    private VerifactuSettingsResponse proposal() {
        return new VerifactuSettingsResponse(false, false, VerifactuMode.VERIFACTU,
                VerifactuEnvironment.TEST, "", "", "01", "S1", "Europe/Madrid",
                VerifactuEnvironment.TEST.qrValidationUrl(),
                softwareName, softwareId, softwareVersion, developerTaxId);
    }
}
