package com.peraerp.finance.currency;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CurrencyService {

    private static final UUID NO_EXCLUDED_ID = new UUID(0, 0);

    private final CurrencyRepository repository;
    private final CurrentCompanyProvider companyProvider;

    public CurrencyService(CurrencyRepository repository, CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public List<CurrencyResponse> findAll() {
        return repository.findAllByCompanyIdOrderByCode(companyProvider.requireCompanyId()).stream()
                .map(CurrencyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CurrencyResponse findById(UUID id) {
        return CurrencyResponse.from(requireById(id));
    }

    @Transactional
    public CurrencyResponse create(CurrencyRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String code = normalize(request.code());
        if (repository.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new BusinessRuleException("Ya existe la moneda " + code + ".");
        }
        validateBase(request.baseCurrency(), request.active());
        if (request.baseCurrency()) {
            repository.clearBaseCurrency(companyId, NO_EXCLUDED_ID);
        }
        CurrencyDefinition currency = new CurrencyDefinition(companyId, code, request.name().trim(),
                request.symbol().trim(), request.decimalPlaces(), request.baseCurrency(), request.active());
        return CurrencyResponse.from(repository.save(currency));
    }

    @Transactional
    public CurrencyResponse update(UUID id, CurrencyRequest request) {
        CurrencyDefinition currency = requireById(id);
        if (!currency.getCode().equalsIgnoreCase(request.code())) {
            throw new BusinessRuleException("El código ISO de una moneda no se puede modificar.");
        }
        validateBase(request.baseCurrency(), request.active());
        if (request.baseCurrency()) {
            repository.clearBaseCurrency(currency.getCompanyId(), currency.getId());
        }
        currency.update(request.name().trim(), request.symbol().trim(), request.decimalPlaces(),
                request.baseCurrency(), request.active());
        return CurrencyResponse.from(currency);
    }

    CurrencyDefinition requireActive(String code) {
        String normalized = normalize(code);
        CurrencyDefinition currency = repository.findByCompanyIdAndCodeIgnoreCase(
                        companyProvider.requireCompanyId(), normalized)
                .orElseThrow(() -> new BusinessRuleException("La moneda " + normalized + " no está configurada."));
        if (!currency.isActive()) {
            throw new BusinessRuleException("La moneda " + normalized + " no está activa.");
        }
        return currency;
    }

    private CurrencyDefinition requireById(UUID id) {
        return repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Moneda", id));
    }

    private void validateBase(boolean baseCurrency, boolean active) {
        if (baseCurrency && !active) {
            throw new BusinessRuleException("La moneda base debe estar activa.");
        }
    }

    private String normalize(String code) {
        return code.trim().toUpperCase();
    }
}
