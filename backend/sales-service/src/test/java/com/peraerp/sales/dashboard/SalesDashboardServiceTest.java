package com.peraerp.sales.dashboard;

import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.currency.CompanyCurrencyClient;
import com.peraerp.sales.document.CommercialDocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDashboardServiceTest {

    @Mock CommercialDocumentRepository repository;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock CompanyCurrencyClient currencyClient;

    @Test
    void comparesCurrentRevenueWithThePreviousMonthAtTheSamePace() {
        UUID companyId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(currencyClient.currentBaseCurrency()).thenReturn("EUR");
        when(repository.findInvoiceRevenue(companyId, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(
                        new InvoiceRevenueEntry(LocalDate.of(2026, 7, 5), new BigDecimal("100")),
                        new InvoiceRevenueEntry(LocalDate.of(2026, 7, 20), new BigDecimal("210")),
                        new InvoiceRevenueEntry(LocalDate.of(2026, 8, 2), new BigDecimal("60")),
                        new InvoiceRevenueEntry(LocalDate.of(2026, 8, 8), new BigDecimal("80"))));
        SalesDashboardService service = new SalesDashboardService(repository, companyProvider, currencyClient, clock);

        SalesDashboardResponse response = service.summarize(6);

        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.currentMonthTotal()).isEqualByComparingTo("140.0000");
        assertThat(response.previousMonthTotal()).isEqualByComparingTo("310.0000");
        assertThat(response.previousMonthToDate()).isEqualByComparingTo("100.0000");
        assertThat(response.expectedByToday()).isEqualByComparingTo("100.0000");
        assertThat(response.varianceAmount()).isEqualByComparingTo("40.0000");
        assertThat(response.performancePercentage()).isEqualByComparingTo("140.00");
        assertThat(response.monthProgressPercentage()).isEqualByComparingTo("32.26");
        assertThat(response.monthlyRevenue()).hasSize(6);
        assertThat(response.dailyRevenue().get(9).currentCumulative()).isEqualByComparingTo("140.0000");
        assertThat(response.dailyRevenue().get(19).currentCumulative()).isNull();
        assertThat(response.dailyRevenue().get(19).previousCumulative()).isEqualByComparingTo("310.0000");
    }

    @Test
    void keepsPerformanceNeutralWhenThereIsNoPreviousMonthBaseline() {
        UUID companyId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-08-10T10:00:00Z"), ZoneOffset.UTC);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(currencyClient.currentBaseCurrency()).thenReturn("EUR");
        when(repository.findInvoiceRevenue(companyId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 10)))
                .thenReturn(List.of(new InvoiceRevenueEntry(LocalDate.of(2026, 8, 5), new BigDecimal("50"))));
        SalesDashboardService service = new SalesDashboardService(repository, companyProvider, currencyClient, clock);

        SalesDashboardResponse response = service.summarize(1);

        assertThat(response.monthlyRevenue()).hasSize(3);
        assertThat(response.currentMonthTotal()).isEqualByComparingTo("50.0000");
        assertThat(response.expectedByToday()).isEqualByComparingTo("0.0000");
        assertThat(response.performancePercentage()).isEqualByComparingTo("0");
        verify(repository).findInvoiceRevenue(companyId, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 10));
    }
}
