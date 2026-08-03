package com.peraerp.sales.document;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

@Component
public class DocumentNumberGenerator {
    private static final Map<DocumentType, String> PREFIXES = Map.of(
            DocumentType.QUOTE, "PRE", DocumentType.DELIVERY_NOTE, "ALB",
            DocumentType.INVOICE, "FAC", DocumentType.WORK_ORDER, "PAR");
    private final DocumentSequenceRepository repository;
    public DocumentNumberGenerator(DocumentSequenceRepository repository) { this.repository = repository; }
    public String next(UUID companyId, DocumentType type, int year) {
        DocumentSequence sequence = repository.findByCompanyIdAndTypeAndYear(companyId, type, year)
                .orElseGet(() -> new DocumentSequence(companyId, type, year));
        long value = sequence.takeNext();
        repository.save(sequence);
        return "%s-%d-%06d".formatted(PREFIXES.get(type), year, value);
    }
}
