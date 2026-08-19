package com.peraerp.sales.verifactu.domain;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Configuración de Veri*Factu de una empresa.
 *
 * <p>{@code issuerTaxId} e {@code issuerLegalName} se copian de identity al activar el módulo y a
 * partir de ahí viven aquí. Si la empresa cambia de denominación social, los registros ya
 * remitidos deben poder reproducirse con los datos que llevaban.</p>
 */
@Entity
@Table(name = "verifactu_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_verifactu_settings_company", columnNames = "company_id"))
public class VerifactuSettings extends CompanyScopedEntity {

    @Column(nullable = false)
    private boolean enabled = false;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private VerifactuMode mode = VerifactuMode.VERIFACTU;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private VerifactuEnvironment environment = VerifactuEnvironment.TEST;
    @Column(name = "issuer_tax_id", nullable = false, length = 20)
    private String issuerTaxId;
    @Column(name = "issuer_legal_name", nullable = false, length = 180)
    private String issuerLegalName;
    @Column(name = "default_regime_key", nullable = false, length = 2)
    private String defaultRegimeKey = "01";
    @Column(name = "default_operation_qualification", nullable = false, length = 2)
    private String defaultOperationQualification = "S1";
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Europe/Madrid";
    @Column(name = "software_name", nullable = false, length = 120)
    private String softwareName;
    @Column(name = "software_id", nullable = false, length = 2)
    private String softwareId;
    @Column(name = "software_version", nullable = false, length = 50)
    private String softwareVersion;
    @Column(name = "developer_tax_id", nullable = false, length = 20)
    private String developerTaxId;

    protected VerifactuSettings() {}

    public VerifactuSettings(UUID companyId, String issuerTaxId, String issuerLegalName,
                             String softwareName, String softwareId, String softwareVersion,
                             String developerTaxId) {
        super(companyId);
        this.issuerTaxId = issuerTaxId;
        this.issuerLegalName = issuerLegalName;
        this.softwareName = softwareName;
        this.softwareId = softwareId;
        this.softwareVersion = softwareVersion;
        this.developerTaxId = developerTaxId;
    }

    public void configure(boolean enabled, VerifactuMode mode, VerifactuEnvironment environment,
                          String issuerTaxId, String issuerLegalName, String defaultRegimeKey,
                          String defaultOperationQualification, String timeZone) {
        this.enabled = enabled;
        this.mode = mode == null ? VerifactuMode.VERIFACTU : mode;
        this.environment = environment == null ? VerifactuEnvironment.TEST : environment;
        this.issuerTaxId = issuerTaxId;
        this.issuerLegalName = issuerLegalName;
        this.defaultRegimeKey = defaultRegimeKey;
        this.defaultOperationQualification = defaultOperationQualification;
        this.timeZone = timeZone;
    }

    /**
     * Sincroniza la identidad del productor del software con la del despliegue actual.
     *
     * <p>Estos cuatro datos identifican a PERA como sistema informático de facturación, no a la
     * empresa. Cuando se actualiza el producto, la configuración debe reflejar la versión que va a
     * generar los registros de aquí en adelante.</p>
     *
     * <p>La reproducibilidad histórica no se resuelve aquí: se resuelve congelando estos datos en
     * cada registro de facturación, que es lo que se remite a la AEAT.</p>
     */
    public void syncSoftwareIdentity(String softwareName, String softwareId, String softwareVersion,
                                     String developerTaxId) {
        this.softwareName = softwareName;
        this.softwareId = softwareId;
        this.softwareVersion = softwareVersion;
        this.developerTaxId = developerTaxId;
    }

    /**
     * Zona horaria de la empresa, la que fija el huso de {@code FechaHoraHusoGenRegistro}.
     *
     * <p>Nunca la del servidor: una instalación multiempresa puede tener empresas en husos
     * distintos, y el registro lleva el huso del obligado.</p>
     */
    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    public boolean isEnabled() { return enabled; }
    public VerifactuMode getMode() { return mode; }
    public VerifactuEnvironment getEnvironment() { return environment; }
    public String getIssuerTaxId() { return issuerTaxId; }
    public String getIssuerLegalName() { return issuerLegalName; }
    public String getDefaultRegimeKey() { return defaultRegimeKey; }
    public String getDefaultOperationQualification() { return defaultOperationQualification; }
    public String getTimeZone() { return timeZone; }
    public String getSoftwareName() { return softwareName; }
    public String getSoftwareId() { return softwareId; }
    public String getSoftwareVersion() { return softwareVersion; }
    public String getDeveloperTaxId() { return developerTaxId; }
}
