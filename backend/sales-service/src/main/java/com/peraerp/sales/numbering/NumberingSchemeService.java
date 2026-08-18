package com.peraerp.sales.numbering;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class NumberingSchemeService {

    private static final UUID NO_EXCLUDED_ID = new UUID(0, 0);

    private final NumberingSchemeRepository repository;
    private final NumberingPatternFormatter formatter;
    private final CurrentCompanyProvider companyProvider;

    public NumberingSchemeService(NumberingSchemeRepository repository, NumberingPatternFormatter formatter,
                                  CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.formatter = formatter;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public Page<NumberingSchemeResponse> search(String query, DocumentType documentType, Boolean active,
                                                 Pageable pageable) {
        String normalized = query == null ? "" : query.trim();
        return repository.search(companyProvider.requireCompanyId(), documentType, active, normalized, pageable)
                .map(NumberingSchemeResponse::from);
    }

    @Transactional(readOnly = true)
    public NumberingSchemeResponse findById(UUID id) {
        return NumberingSchemeResponse.from(requireScheme(id));
    }

    @Transactional
    public NumberingSchemeResponse create(NumberingSchemeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalizeCode(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe una numeración con el código " + code + ".");
        }
        validate(request);
        if (request.defaultScheme()) {
            repository.clearDefault(companyId, request.documentType(), NO_EXCLUDED_ID);
        }
        NumberingScheme scheme = new NumberingScheme(companyId, code, request.name().trim(), request.documentType(),
                normalizeCode(request.series()), request.pattern().trim(), request.resetPeriod(),
                request.initialValue(), request.active(), request.defaultScheme());
        return NumberingSchemeResponse.from(repository.save(scheme));
    }

    @Transactional
    public NumberingSchemeResponse update(UUID id, NumberingSchemeRequest request) {
        NumberingScheme scheme = requireScheme(id);
        if (!scheme.getCode().equalsIgnoreCase(request.code())) {
            throw new BusinessRuleException("El código de la numeración no se puede modificar.");
        }
        if (scheme.getDocumentType() != request.documentType()) {
            throw new BusinessRuleException("El tipo documental de una numeración en uso no se puede modificar.");
        }
        validate(request);
        if (request.defaultScheme()) {
            repository.clearDefault(scheme.getCompanyId(), scheme.getDocumentType(), scheme.getId());
        }
        scheme.update(request.name().trim(), normalizeCode(request.series()), request.pattern().trim(),
                request.resetPeriod(), request.initialValue(), request.active(), request.defaultScheme());
        return NumberingSchemeResponse.from(scheme);
    }

    @Transactional(readOnly = true)
    public NumberingPreviewResponse preview(UUID id, LocalDate date, Long sequence) {
        NumberingScheme scheme = requireScheme(id);
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        long effectiveSequence = sequence == null ? scheme.getInitialValue() : sequence;
        if (effectiveSequence < 1) {
            throw new BusinessRuleException("La secuencia de la vista previa debe ser positiva.");
        }
        return new NumberingPreviewResponse(scheme.getId(), effectiveDate, effectiveSequence,
                formatter.format(scheme.getPattern(), scheme.getSeries(), effectiveDate, effectiveSequence));
    }

    private NumberingScheme requireScheme(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Numeración", id));
    }

    private void validate(NumberingSchemeRequest request) {
        if (request.defaultScheme() && !request.active()) {
            throw new BusinessRuleException("La numeración predeterminada debe estar activa.");
        }
        formatter.validate(request.pattern().trim());
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }
}
