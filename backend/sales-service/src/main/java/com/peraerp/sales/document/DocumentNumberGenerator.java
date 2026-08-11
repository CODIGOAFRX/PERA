package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.numbering.NumberingCounter;
import com.peraerp.sales.numbering.NumberingCounterRepository;
import com.peraerp.sales.numbering.NumberingPatternFormatter;
import com.peraerp.sales.numbering.NumberingResetPeriod;
import com.peraerp.sales.numbering.NumberingScheme;
import com.peraerp.sales.numbering.NumberingSchemeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Component
public class DocumentNumberGenerator {

    private static final int MAX_OCCUPIED_NUMBERS_TO_SKIP = 10_000;

    private static final Map<DocumentType, String> PREFIXES = Map.of(
            DocumentType.QUOTE, "PRE",
            DocumentType.SALES_ORDER, "PED",
            DocumentType.DELIVERY_NOTE, "ALB",
            DocumentType.INVOICE, "FAC",
            DocumentType.WORK_ORDER, "PAR");

    private final NumberingSchemeRepository schemes;
    private final NumberingCounterRepository counters;
    private final NumberingPatternFormatter formatter;
    private final CommercialDocumentRepository documents;

    public DocumentNumberGenerator(NumberingSchemeRepository schemes, NumberingCounterRepository counters,
                                   NumberingPatternFormatter formatter, CommercialDocumentRepository documents) {
        this.schemes = schemes;
        this.counters = counters;
        this.formatter = formatter;
        this.documents = documents;
    }

    @Transactional
    public String next(UUID companyId, DocumentType type, LocalDate date, UUID requestedSchemeId) {
        NumberingScheme scheme = resolveScheme(companyId, type, requestedSchemeId);
        String periodKey = periodKey(scheme.getResetPeriod(), date);
        Instant now = Instant.now();
        counters.ensureCounter(UUID.randomUUID(), companyId, scheme.getId(), periodKey,
                scheme.getInitialValue(), now);
        NumberingCounter counter = counters.findByCompanyIdAndSchemeIdAndPeriodKey(companyId, scheme.getId(), periodKey)
                .orElseThrow(() -> new IllegalStateException("No se pudo inicializar el contador de numeración."));
        for (int attempt = 0; attempt < MAX_OCCUPIED_NUMBERS_TO_SKIP; attempt++) {
            long value = counter.takeNext();
            String candidate = formatter.format(scheme.getPattern(), scheme.getSeries(), date, value);
            if (!documents.existsByCompanyIdAndTypeAndDocumentNumber(companyId, type, candidate)) {
                counters.save(counter);
                return candidate;
            }
        }
        throw new BusinessRuleException(
                "No se pudo obtener un número libre. Revisa el contador y el patrón de la numeración seleccionada.");
    }

    public String next(UUID companyId, DocumentType type, int year) {
        return next(companyId, type, LocalDate.of(year, 1, 1), null);
    }

    private NumberingScheme resolveScheme(UUID companyId, DocumentType type, UUID requestedSchemeId) {
        if (requestedSchemeId != null) {
            NumberingScheme requested = schemes.findByIdAndCompanyId(requestedSchemeId, companyId)
                    .orElseThrow(() -> new BusinessRuleException("La numeración indicada no existe en la empresa activa."));
            if (!requested.isActive() || requested.getDocumentType() != type) {
                throw new BusinessRuleException("La numeración no está activa o no corresponde al tipo documental.");
            }
            return requested;
        }
        return schemes.findByCompanyIdAndDocumentTypeAndDefaultSchemeTrueAndActiveTrue(companyId, type)
                .orElseGet(() -> createAndLoadDefault(companyId, type));
    }

    private NumberingScheme createAndLoadDefault(UUID companyId, DocumentType type) {
        String series = PREFIXES.get(type);
        schemes.ensureDefault(UUID.randomUUID(), companyId, "AUTO-" + type.name(),
                "Numeración " + type.name(), type.name(), series, "{series}-{yyyy}-{seq:6}", Instant.now());
        return schemes.findByCompanyIdAndDocumentTypeAndDefaultSchemeTrueAndActiveTrue(companyId, type)
                .orElseThrow(() -> new BusinessRuleException(
                        "No existe una numeración predeterminada activa para " + type + "."));
    }

    private String periodKey(NumberingResetPeriod resetPeriod, LocalDate date) {
        return switch (resetPeriod) {
            case YEARLY -> date.format(DateTimeFormatter.ofPattern("yyyy"));
            case MONTHLY -> date.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case DAILY -> date.format(DateTimeFormatter.BASIC_ISO_DATE);
            case NEVER -> "ALL";
        };
    }
}
