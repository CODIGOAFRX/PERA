package com.peraerp.sales.masterdata;

import com.peraerp.sales.verifactu.domain.TaxIdentificationType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos del cliente que ventas congela al emitir un documento.
 *
 * <p>Los tres campos fiscales son los que alimentan el bloque {@code IDDestinatario} del registro
 * de facturación. Se leen de maestros una sola vez, al crear el documento, y a partir de ahí viven
 * en la propia factura: un registro remitido a la AEAT tiene que poder reconstruirse aunque el
 * cliente cambie de NIF o se dé de baja.</p>
 */
public record CustomerSnapshot(UUID id, String code, String legalName, boolean active,
                               String taxId, TaxIdentificationType taxIdentificationType,
                               String taxCountryCode, String email, com.peraerp.platform.domain.ContactDetails details,
                               BigDecimal creditLimit, BigDecimal riskWarningThreshold, String riskPolicy) {
    /** Sin datos de riesgo: el cliente no tiene límite ni política que aplicar. */
    public CustomerSnapshot(UUID id, String code, String legalName, boolean active, String taxId,
                            TaxIdentificationType taxIdentificationType, String taxCountryCode, String email,
                            com.peraerp.platform.domain.ContactDetails details) {
        this(id, code, legalName, active, taxId, taxIdentificationType, taxCountryCode, email, details, null, null, null);
    }

    public CustomerSnapshot(UUID id, String code, String legalName, boolean active, String taxId, TaxIdentificationType taxIdentificationType, String taxCountryCode) {
        this(id, code, legalName, active, taxId, taxIdentificationType, taxCountryCode, null, null);
    }

    /** Constructor de compatibilidad para los usos que todavía no necesitan datos fiscales. */
    public CustomerSnapshot(UUID id, String code, String legalName, boolean active) {
        this(id, code, legalName, active, null, null, null);
    }
}
