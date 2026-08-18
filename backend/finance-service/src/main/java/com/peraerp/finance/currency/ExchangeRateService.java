package com.peraerp.finance.currency;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExchangeRateService {

    private final ExchangeRateRepository repository;
    private final CurrencyService currencyService;
    private final CurrentCompanyProvider companyProvider;

    public ExchangeRateService(ExchangeRateRepository repository, CurrencyService currencyService,
                               CurrentCompanyProvider companyProvider) {
        this.repository = repository;
        this.currencyService = currencyService;
        this.companyProvider = companyProvider;
    }

    @Transactional(readOnly = true)
    public Page<ExchangeRateResponse> search(String baseCode, String quoteCode, LocalDate fromDate,
                                              LocalDate toDate, Boolean active, Pageable pageable) {
        return repository.search(companyProvider.requireCompanyId(), normalizeNullable(baseCode),
                        normalizeNullable(quoteCode), fromDate, toDate, active, pageable)
                .map(ExchangeRateResponse::from);
    }

    @Transactional
    public ExchangeRateResponse create(ExchangeRateRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String base = normalize(request.baseCode());
        String quote = normalize(request.quoteCode());
        if (base.equals(quote)) {
            throw new BusinessRuleException("Las monedas origen y destino deben ser diferentes.");
        }
        currencyService.requireActive(base);
        currencyService.requireActive(quote);
        String source = request.source().trim();
        if (repository.existsByCompanyIdAndBaseCodeAndQuoteCodeAndRateDateAndSourceIgnoreCase(
                companyId, base, quote, request.rateDate(), source)) {
            throw new BusinessRuleException("Ya existe ese tipo de cambio para la fecha y fuente indicadas.");
        }
        ExchangeRate rate = new ExchangeRate(companyId, base, quote, request.rate(), request.rateDate(),
                source, request.active());
        return ExchangeRateResponse.from(repository.save(rate));
    }

    @Transactional
    public ExchangeRateResponse update(UUID id, ExchangeRateUpdateRequest request) {
        ExchangeRate rate = repository.findByIdAndCompanyId(id, companyProvider.requireCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de cambio", id));
        rate.update(request.rate(), request.active());
        return ExchangeRateResponse.from(rate);
    }

    @Transactional(readOnly = true)
    public CurrencyConversionResponse convert(CurrencyConversionRequest request) {
        UUID companyId = companyProvider.requireCompanyId();
        String from = normalize(request.fromCurrency());
        String to = normalize(request.toCurrency());
        CurrencyDefinition sourceCurrency = currencyService.requireActive(from);
        CurrencyDefinition targetCurrency = currencyService.requireActive(to);
        if (sourceCurrency.getCode().equals(targetCurrency.getCode())) {
            return new CurrencyConversionResponse(request.amount(), from,
                    request.amount().setScale(targetCurrency.getDecimalPlaces(), RoundingMode.HALF_UP), to,
                    BigDecimal.ONE, request.date(), request.date(), "IDENTITY", null, false);
        }

        Optional<ExchangeRate> direct = repository
                .findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
                        companyId, from, to, request.date());
        ExchangeRate selected;
        BigDecimal effectiveRate;
        boolean inverse;
        if (direct.isPresent()) {
            selected = direct.get();
            effectiveRate = selected.getRate();
            inverse = false;
        } else {
            selected = repository
                    .findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
                            companyId, to, from, request.date())
                    .orElseThrow(() -> new BusinessRuleException(
                            "No existe un tipo de cambio aplicable de " + from + " a " + to + "."));
            effectiveRate = BigDecimal.ONE.divide(selected.getRate(), 10, RoundingMode.HALF_UP);
            inverse = true;
        }
        BigDecimal converted = request.amount().multiply(effectiveRate)
                .setScale(targetCurrency.getDecimalPlaces(), RoundingMode.HALF_UP);
        return new CurrencyConversionResponse(request.amount(), from, converted, to, effectiveRate,
                request.date(), selected.getRateDate(), selected.getSource(), selected.getId(), inverse);
    }

    private String normalize(String code) {
        return code.trim().toUpperCase();
    }

    private String normalizeNullable(String code) {
        return code == null || code.isBlank() ? null : normalize(code);
    }
}
