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

    // Comportamiento de la serie (ver SeriesOptions; campo de fseries de Dimpro entre paréntesis).
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(length = 16) private String color;
    @Column(name = "listing_group", length = 60) private String listingGroup;
    @Column(name = "next_scheme_id") private UUID nextSchemeId;
    @Column(name = "same_number_as_source", nullable = false) private boolean sameNumberAsSource;
    @Column(name = "same_date_as_source", nullable = false) private boolean sameDateAsSource;
    @Column(name = "asks_number", nullable = false) private boolean asksNumber;
    @Column(name = "carries_notes", nullable = false) private boolean carriesNotes = true;
    @Column(name = "footer_text", length = 200) private String footerText;
    @Column(name = "print_copies", nullable = false) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.SMALLINT) private int printCopies;
    @Column(nullable = false) private boolean priced = true;
    @Column(nullable = false) private boolean internal;
    @Column(name = "zero_amounts", nullable = false) private boolean zeroAmounts;
    @Column(name = "counts_for_risk", nullable = false) private boolean countsForRisk = true;
    @Column(name = "remind_after_days", nullable = false) @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.SMALLINT) private int remindAfterDays;

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

    /** Aplica el comportamiento ya validado por el servicio. */
    public void configure(SeriesOptions options) {
        this.displayOrder = options.displayOrder();
        this.color = blankToNull(options.color());
        this.listingGroup = blankToNull(options.listingGroup());
        this.nextSchemeId = options.nextSchemeId();
        this.sameNumberAsSource = options.sameNumberAsSource();
        this.sameDateAsSource = options.sameDateAsSource();
        this.asksNumber = options.asksNumber();
        this.carriesNotes = options.carriesNotes();
        this.footerText = blankToNull(options.footerText());
        this.printCopies = options.printCopies();
        this.priced = options.priced();
        this.internal = options.internal();
        this.zeroAmounts = options.zeroAmounts();
        this.countsForRisk = options.countsForRisk();
        this.remindAfterDays = options.remindAfterDays();
    }

    public SeriesOptions options() {
        return new SeriesOptions(displayOrder, color, listingGroup, nextSchemeId, sameNumberAsSource, sameDateAsSource,
                asksNumber, carriesNotes, footerText, printCopies, priced, internal, zeroAmounts, countsForRisk,
                remindAfterDays);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
