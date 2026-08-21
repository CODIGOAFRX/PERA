package com.peraerp.masterdata.catalog;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tax_codes", uniqueConstraints = @UniqueConstraint(
        name = "uk_tax_code_country", columnNames = {"company_id", "country_code", "code"}))
public class TaxCode extends CompanyScopedEntity {
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 140)
    private String name;
    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    /**
     * Se conserva porque hay pantallas y snapshots que lo leen, pero ya no es un dato
     * independiente: se deriva de {@link #operationQualification}. Dos fuentes de verdad sobre si
     * algo está exento acaban contradiciéndose.
     */
    @Column(nullable = false)
    private boolean exempt;
    @Enumerated(EnumType.STRING) @Column(name = "operation_qualification", nullable = false, length = 24)
    private OperationQualification operationQualification = OperationQualification.SUBJECT_NOT_EXEMPT;
    @Enumerated(EnumType.STRING) @Column(name = "exemption_cause", length = 24)
    private ExemptionCause exemptionCause;
    @Column(name = "regime_key", nullable = false, length = 2)
    private String regimeKey = "01";
    @Column(nullable = false)
    private boolean active = true;

    protected TaxCode() {}

    /** Constructor de compatibilidad: deduce la calificación del booleano heredado. */
    public TaxCode(UUID companyId, String countryCode, String code, String name, BigDecimal percentage,
                   LocalDate validFrom, LocalDate validUntil, boolean exempt, boolean active) {
        this(companyId, countryCode, code, name, percentage, validFrom, validUntil,
                exempt ? OperationQualification.EXEMPT : OperationQualification.SUBJECT_NOT_EXEMPT,
                exempt ? ExemptionCause.OTHER : null, "01", active);
    }

    public TaxCode(UUID companyId, String countryCode, String code, String name, BigDecimal percentage,
                   LocalDate validFrom, LocalDate validUntil, OperationQualification operationQualification,
                   ExemptionCause exemptionCause, String regimeKey, boolean active) {
        super(companyId);
        this.countryCode = countryCode;
        this.code = code;
        this.name = name;
        this.percentage = percentage;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = active;
        applyFiscalQualification(operationQualification, exemptionCause, regimeKey);
    }

    /** Actualización de compatibilidad: conserva la calificación fiscal ya guardada. */
    public void update(String countryCode, String name, BigDecimal percentage, LocalDate validFrom,
                       LocalDate validUntil, boolean exempt, boolean active) {
        update(countryCode, name, percentage, validFrom, validUntil,
                exempt == (operationQualification == OperationQualification.EXEMPT)
                        ? operationQualification
                        : (exempt ? OperationQualification.EXEMPT : OperationQualification.SUBJECT_NOT_EXEMPT),
                exempt ? (exemptionCause == null ? ExemptionCause.OTHER : exemptionCause) : null,
                regimeKey, active);
    }

    public void update(String countryCode, String name, BigDecimal percentage, LocalDate validFrom,
                       LocalDate validUntil, OperationQualification operationQualification,
                       ExemptionCause exemptionCause, String regimeKey, boolean active) {
        this.countryCode = countryCode;
        this.name = name;
        this.percentage = percentage;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.active = active;
        applyFiscalQualification(operationQualification, exemptionCause, regimeKey);
    }

    /**
     * Fija la calificación fiscal y mantiene coherentes el booleano heredado y la causa de exención.
     *
     * <p>Una operación exenta tiene que decir por qué lo está, y una que no lo está no puede
     * arrastrar una causa. Es lo que impide que un código fiscal declare una cosa y su desglose
     * declare otra.</p>
     */
    private void applyFiscalQualification(OperationQualification qualification, ExemptionCause cause,
                                          String regimeKey) {
        this.operationQualification = qualification == null
                ? OperationQualification.SUBJECT_NOT_EXEMPT : qualification;
        if (this.operationQualification.isExempt()) {
            if (cause == null) {
                throw new BusinessRuleException(
                        "Un código fiscal exento debe indicar la causa de exención (E1 a E6).");
            }
            this.exemptionCause = cause;
        } else {
            this.exemptionCause = null;
        }
        this.exempt = this.operationQualification.isExempt();
        if (this.exempt && percentage != null && percentage.signum() != 0) {
            throw new BusinessRuleException("Un código fiscal exento no puede tener porcentaje.");
        }
        String normalizedRegime = regimeKey == null || regimeKey.isBlank() ? "01" : regimeKey.trim();
        if (!normalizedRegime.matches("\\d{2}")) {
            throw new BusinessRuleException(
                    "La clave de régimen debe tener dos dígitos; se indicó «" + normalizedRegime + "».");
        }
        this.regimeKey = normalizedRegime;
    }

    public boolean isApplicableOn(LocalDate date) {
        return active && !date.isBefore(validFrom) && (validUntil == null || !date.isAfter(validUntil));
    }

    public String getCountryCode() { return countryCode; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getPercentage() { return percentage; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public boolean isExempt() { return exempt; }
    public OperationQualification getOperationQualification() { return operationQualification; }
    public ExemptionCause getExemptionCause() { return exemptionCause; }
    public String getRegimeKey() { return regimeKey; }
    public boolean isActive() { return active; }
}
