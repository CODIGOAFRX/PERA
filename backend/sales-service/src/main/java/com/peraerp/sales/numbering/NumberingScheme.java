package com.peraerp.sales.numbering;

import com.peraerp.platform.domain.CompanyScopedEntity;
import com.peraerp.sales.document.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "numbering_schemes", uniqueConstraints =
        @UniqueConstraint(name = "uk_numbering_scheme_code", columnNames = {"company_id", "code"}))
public class NumberingScheme extends CompanyScopedEntity {

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30, updatable = false)
    private DocumentType documentType;

    @Column(nullable = false, length = 20)
    private String series;

    @Column(nullable = false, length = 120)
    private String pattern;

    @Enumerated(EnumType.STRING)
    @Column(name = "reset_period", nullable = false, length = 20)
    private NumberingResetPeriod resetPeriod;

    @Column(name = "initial_value", nullable = false)
    private long initialValue;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "default_scheme", nullable = false)
    private boolean defaultScheme;

    protected NumberingScheme() {
    }

    public NumberingScheme(UUID companyId, String code, String name, DocumentType documentType, String series,
                           String pattern, NumberingResetPeriod resetPeriod, long initialValue,
                           boolean active, boolean defaultScheme) {
        super(companyId);
        this.code = code;
        this.name = name;
        this.documentType = documentType;
        this.series = series;
        this.pattern = pattern;
        this.resetPeriod = resetPeriod;
        this.initialValue = initialValue;
        this.active = active;
        this.defaultScheme = defaultScheme;
    }

    public void update(String name, String series, String pattern, NumberingResetPeriod resetPeriod,
                       long initialValue, boolean active, boolean defaultScheme) {
        this.name = name;
        this.series = series;
        this.pattern = pattern;
        this.resetPeriod = resetPeriod;
        this.initialValue = initialValue;
        this.active = active;
        this.defaultScheme = defaultScheme;
    }

    public void removeDefault() {
        this.defaultScheme = false;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public DocumentType getDocumentType() { return documentType; }
    public String getSeries() { return series; }
    public String getPattern() { return pattern; }
    public NumberingResetPeriod getResetPeriod() { return resetPeriod; }
    public long getInitialValue() { return initialValue; }
    public boolean isActive() { return active; }
    public boolean isDefaultScheme() { return defaultScheme; }
}
