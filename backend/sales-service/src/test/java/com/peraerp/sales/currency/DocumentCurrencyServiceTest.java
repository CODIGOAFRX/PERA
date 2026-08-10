package com.peraerp.sales.currency;

import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentCurrencyServiceTest {

    private CompanyCurrencyClient companyCurrencies;
    private ExchangeRateClient rates;
    private DocumentCurrencyService service;
    private final LocalDate date = LocalDate.of(2026, 8, 10);

    @BeforeEach
    void setUp() {
        companyCurrencies = mock(CompanyCurrencyClient.class);
        rates = mock(ExchangeRateClient.class);
        service = new DocumentCurrencyService(companyCurrencies, rates);
    }

    @Test
    void baseCurrencyUsesIdentitySnapshotWithoutRemoteRate() {
        when(companyCurrencies.currentBaseCurrency()).thenReturn("EUR");

        DocumentCurrencySnapshot snapshot = service.resolve("eur", date);

        assertThat(snapshot.baseCurrency()).isEqualTo("EUR");
        assertThat(snapshot.exchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(snapshot.source()).isEqualTo("IDENTITY");
        verifyNoInteractions(rates);
    }

    @Test
    void foreignCurrencyUsesResolvedRateSnapshot() {
        when(companyCurrencies.currentBaseCurrency()).thenReturn("EUR");
        when(rates.resolve("USD", "EUR", date)).thenReturn(
                new ExchangeRateClient.ResolvedExchangeRate(new BigDecimal("0.86"), date.minusDays(1), "ECB"));

        DocumentCurrencySnapshot snapshot = service.resolve("USD", date);

        assertThat(snapshot.exchangeRate()).isEqualByComparingTo("0.86");
        assertThat(snapshot.rateDate()).isEqualTo(date.minusDays(1));
        assertThat(snapshot.source()).isEqualTo("ECB");
    }

    @Test
    void rejectsInvalidCodesAndNonPositiveRates() {
        assertThatThrownBy(() -> service.resolve("EURO", date)).isInstanceOf(BusinessRuleException.class);

        when(companyCurrencies.currentBaseCurrency()).thenReturn("EUR");
        when(rates.resolve("USD", "EUR", date)).thenReturn(
                new ExchangeRateClient.ResolvedExchangeRate(BigDecimal.ZERO, date, "MANUAL"));
        assertThatThrownBy(() -> service.resolve("USD", date))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("positivo");
    }
}
