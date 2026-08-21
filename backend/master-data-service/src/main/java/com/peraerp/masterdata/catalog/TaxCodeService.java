package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaxCodeService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final TaxCodeRepository repository;
    private final CurrentCompanyProvider companyProvider;

    public TaxCodeService(TaxCodeRepository repository, CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.companyProvider = companyProvider;
    }

    @Transactional
    public TaxCodeResponse create(TaxCodeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String countryCode = normalizeCountry(request.countryCode());
        String code = normalizeCode(request.code());
        validateRules(request.percentage(), request.validFrom(), request.validUntil(), request.exempt());
        if (repository.existsByCompanyIdAndCountryCodeAndCodeIgnoreCase(companyId, countryCode, code)) {
            throw new BusinessRuleException("Ya existe el código fiscal " + code + " para el país " +
                    countryCode + ".");
        }
        return TaxCodeResponse.from(repository.save(new TaxCode(companyId, countryCode, code,
                request.name().trim(), request.percentage(), request.validFrom(), request.validUntil(),
                qualification(request), exemptionCause(request), request.regimeKey(), request.active())));
    }

    @Transactional(readOnly = true)
    public TaxCodeResponse findById(UUID id) {
        return TaxCodeResponse.from(requireTaxCode(id, companyProvider.requireCompanyId()));
    }

    @Transactional(readOnly = true)
    public Page<TaxCodeResponse> search(String query, String countryCode, Boolean active, LocalDate validOn,
                                        Pageable pageable) {
        String normalizedCountry = countryCode == null || countryCode.isBlank()
                ? null : normalizeCountry(countryCode);
        return repository.search(companyProvider.requireCompanyId(), normalizeQuery(query), normalizedCountry,
                active, validOn, pageable).map(TaxCodeResponse::from);
    }

    @Transactional
    public TaxCodeResponse update(UUID id, TaxCodeRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        TaxCode taxCode = requireTaxCode(id, companyId);
        String countryCode = normalizeCountry(request.countryCode());
        String code = normalizeCode(request.code());
        if (!taxCode.getCode().equalsIgnoreCase(code)) {
            throw new BusinessRuleException("El código fiscal no se puede modificar.");
        }
        validateRules(request.percentage(), request.validFrom(), request.validUntil(), request.exempt());
        if (!taxCode.getCountryCode().equals(countryCode)
                && repository.existsByCompanyIdAndCountryCodeAndCodeIgnoreCase(companyId, countryCode, code)) {
            throw new BusinessRuleException("Ya existe el código fiscal " + code + " para el país " +
                    countryCode + ".");
        }
        taxCode.update(countryCode, request.name().trim(), request.percentage(), request.validFrom(),
                request.validUntil(), qualification(request), exemptionCause(request), request.regimeKey(),
                request.active());
        return TaxCodeResponse.from(taxCode);
    }

    TaxCode requireApplicable(UUID id, UUID companyId, LocalDate date) {
        TaxCode taxCode = requireTaxCode(id, companyId);
        if (!taxCode.isApplicableOn(date)) {
            throw new BusinessRuleException("El código fiscal seleccionado no está activo o vigente en la fecha " +
                    date + ".");
        }
        return taxCode;
    }

    private TaxCode requireTaxCode(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Código fiscal", id));
    }

    private void validateRules(BigDecimal percentage, LocalDate validFrom, LocalDate validUntil, boolean exempt) {
        if (percentage == null || percentage.signum() < 0 || percentage.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessRuleException("El porcentaje fiscal debe estar entre 0 y 100.");
        }
        if (validFrom == null) {
            throw new BusinessRuleException("La fecha inicial de vigencia es obligatoria.");
        }
        if (validUntil != null && validUntil.isBefore(validFrom)) {
            throw new BusinessRuleException("La fecha final de vigencia no puede ser anterior a la inicial.");
        }
        if (exempt && percentage.signum() != 0) {
            throw new BusinessRuleException("Un código fiscal exento debe tener porcentaje cero.");
        }
    }

    private String normalizeCountry(String countryCode) {
        String normalized = countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new BusinessRuleException("El país debe indicarse con un código ISO-3166 alpha-2.");
        }
        return normalized;
    }
    private String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }
    private String normalizeQuery(String query) { return query == null || query.isBlank() ? "" : query.trim(); }

    /**
     * Resuelve la calificación fiscal admitiendo peticiones antiguas.
     *
     * <p>Una pantalla que todavía envíe solo el booleano {@code exempt} sigue funcionando; en
     * cuanto envía {@code operationQualification}, manda esa. Así el cambio no rompe integraciones
     * existentes pero tampoco obliga a arrastrar el booleano para siempre.</p>
     */
    private static OperationQualification qualification(TaxCodeRequest request) {
        if (request.operationQualification() != null) {
            return request.operationQualification();
        }
        return request.exempt() ? OperationQualification.EXEMPT : OperationQualification.SUBJECT_NOT_EXEMPT;
    }

    /**
     * Causa de exención, con E6 («otras causas») como valor por defecto.
     *
     * <p>E6 es la única causa que no afirma nada que no sepamos: quien marque un código como exento
     * sin precisar el motivo queda declarado de la forma más conservadora posible, y puede
     * corregirlo. La alternativa —rechazar la petición— rompería las pantallas que aún no envían
     * el campo.</p>
     */
    private static ExemptionCause exemptionCause(TaxCodeRequest request) {
        if (!qualification(request).isExempt()) {
            return null;
        }
        return request.exemptionCause() == null ? ExemptionCause.OTHER : request.exemptionCause();
    }
}
