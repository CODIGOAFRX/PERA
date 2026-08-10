package com.peraerp.finance.currency;

import com.peraerp.finance.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeRateServiceTest {

    private ExchangeRateRepository repository;
    private CurrencyService currencies;
    private ExchangeRateService service;
    private UUID companyId;
    private final LocalDate date = LocalDate.of(2026, 8, 10);

    @BeforeEach
    void setUp() {
        repository = mock(ExchangeRateRepository.class);
        currencies = mock(CurrencyService.class);
        CurrentCompanyProvider companyProvider = mock(CurrentCompanyProvider.class);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(currencies.requireActive("EUR")).thenReturn(currency("EUR", "€", 2));
        when(currencies.requireActive("USD")).thenReturn(currency("USD", "$", 2));
        service = new ExchangeRateService(repository, currencies, companyProvider);
    }

    @Test
    void createsNormalizedExchangeRate() {
        when(repository.save(any(ExchangeRate.class))).thenAnswer(invocation -> {
            ExchangeRate rate = invocation.getArgument(0);
            ReflectionTestUtils.setField(rate, "id", UUID.randomUUID());
            return rate;
        });

        ExchangeRateResponse response = service.create(new ExchangeRateRequest("eur", "usd",
                new BigDecimal("1.15"), date, "ECB", true));

        assertThat(response.baseCode()).isEqualTo("EUR");
        assertThat(response.quoteCode()).isEqualTo("USD");
        assertThat(response.rate()).isEqualByComparingTo("1.15");
    }

    @Test
    void rejectsSameCurrencyPairAndDuplicateRate() {
        assertThatThrownBy(() -> service.create(new ExchangeRateRequest("EUR", "EUR", BigDecimal.ONE,
                date, "ECB", true))).isInstanceOf(BusinessRuleException.class);

        when(repository.existsByCompanyIdAndBaseCodeAndQuoteCodeAndRateDateAndSourceIgnoreCase(
                companyId, "EUR", "USD", date, "ECB")).thenReturn(true);
        assertThatThrownBy(() -> service.create(new ExchangeRateRequest("EUR", "USD", BigDecimal.ONE,
                date, "ECB", true))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Ya existe");
    }

    @Test
    void convertsUsingLatestDirectRateAndTargetPrecision() {
        ExchangeRate rate = rate("EUR", "USD", "1.125", date.minusDays(1));
        when(repository.findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
                companyId, "EUR", "USD", date)).thenReturn(Optional.of(rate));

        CurrencyConversionResponse response = service.convert(
                new CurrencyConversionRequest(new BigDecimal("10"), "EUR", "USD", date));

        assertThat(response.targetAmount()).isEqualByComparingTo("11.25");
        assertThat(response.exchangeRate()).isEqualByComparingTo("1.125");
        assertThat(response.inverseRate()).isFalse();
        assertThat(response.rateDate()).isEqualTo(date.minusDays(1));
    }

    @Test
    void canResolveInverseRateWithoutDuplicatingData() {
        when(repository.findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
                companyId, "USD", "EUR", date)).thenReturn(Optional.empty());
        ExchangeRate inverse = rate("EUR", "USD", "1.25", date);
        when(repository.findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
                companyId, "EUR", "USD", date)).thenReturn(Optional.of(inverse));

        CurrencyConversionResponse response = service.convert(
                new CurrencyConversionRequest(new BigDecimal("10"), "USD", "EUR", date));

        assertThat(response.exchangeRate()).isEqualByComparingTo("0.8000000000");
        assertThat(response.targetAmount()).isEqualByComparingTo("8.00");
        assertThat(response.inverseRate()).isTrue();
    }

    @Test
    void sameCurrencyUsesIdentityRateAndMissingPairIsRejected() {
        CurrencyConversionResponse identity = service.convert(
                new CurrencyConversionRequest(new BigDecimal("10.125"), "EUR", "EUR", date));
        assertThat(identity.targetAmount()).isEqualByComparingTo("10.13");
        assertThat(identity.rateSource()).isEqualTo("IDENTITY");

        when(repository.findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
                companyId, "EUR", "USD", date)).thenReturn(Optional.empty());
        when(repository.findFirstByCompanyIdAndBaseCodeAndQuoteCodeAndActiveTrueAndRateDateLessThanEqualOrderByRateDateDesc(
                companyId, "USD", "EUR", date)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.convert(
                new CurrencyConversionRequest(BigDecimal.ONE, "EUR", "USD", date)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("No existe");
    }

    private CurrencyDefinition currency(String code, String symbol, int decimals) {
        return new CurrencyDefinition(companyId, code, code, symbol, decimals, code.equals("EUR"), true);
    }

    private ExchangeRate rate(String base, String quote, String value, LocalDate rateDate) {
        ExchangeRate rate = new ExchangeRate(companyId, base, quote, new BigDecimal(value), rateDate, "ECB", true);
        ReflectionTestUtils.setField(rate, "id", UUID.randomUUID());
        return rate;
    }
}
