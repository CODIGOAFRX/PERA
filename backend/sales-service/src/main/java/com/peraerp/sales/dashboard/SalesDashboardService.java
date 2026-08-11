package com.peraerp.sales.dashboard;

import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.currency.CompanyCurrencyClient;
import com.peraerp.sales.document.CommercialDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalesDashboardService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final CommercialDocumentRepository documentRepository;
    private final CurrentCompanyProvider companyProvider;
    private final CompanyCurrencyClient currencyClient;
    private final Clock clock;

    @Autowired
    public SalesDashboardService(CommercialDocumentRepository documentRepository,
                                 CurrentCompanyProvider companyProvider,
                                 CompanyCurrencyClient currencyClient) {
        this(documentRepository, companyProvider, currencyClient, Clock.systemDefaultZone());
    }

    SalesDashboardService(CommercialDocumentRepository documentRepository,
                          CurrentCompanyProvider companyProvider,
                          CompanyCurrencyClient currencyClient,
                          Clock clock) {
        this.documentRepository = documentRepository;
        this.companyProvider = companyProvider;
        this.currencyClient = currencyClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SalesDashboardResponse summarize(int requestedMonths) {
        int months = Math.max(3, Math.min(12, requestedMonths));
        LocalDate today = LocalDate.now(clock);
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        List<InvoiceRevenueEntry> revenue = documentRepository.findInvoiceRevenue(
                companyProvider.requireCompanyId(), firstMonth.atDay(1), today);

        Map<YearMonth, BigDecimal> totals = new LinkedHashMap<>();
        for (int index = 0; index < months; index++) {
            totals.put(firstMonth.plusMonths(index), BigDecimal.ZERO);
        }
        revenue.forEach(entry -> totals.computeIfPresent(YearMonth.from(entry.issueDate()),
                (month, total) -> total.add(entry.amount())));

        YearMonth previousMonth = currentMonth.minusMonths(1);
        BigDecimal currentTotal = totals.getOrDefault(currentMonth, BigDecimal.ZERO);
        BigDecimal previousTotal = totals.getOrDefault(previousMonth, BigDecimal.ZERO);
        int comparablePreviousDay = Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth());
        BigDecimal previousToDate = totalThroughDay(revenue, previousMonth, comparablePreviousDay);
        BigDecimal expectedByToday = previousTotal
                .multiply(BigDecimal.valueOf(today.getDayOfMonth()))
                .divide(BigDecimal.valueOf(currentMonth.lengthOfMonth()), 4, RoundingMode.HALF_UP);
        BigDecimal variance = currentTotal.subtract(expectedByToday);
        BigDecimal performance = expectedByToday.signum() == 0
                ? BigDecimal.ZERO
                : currentTotal.multiply(ONE_HUNDRED).divide(expectedByToday, 2, RoundingMode.HALF_UP);
        BigDecimal progress = BigDecimal.valueOf(today.getDayOfMonth())
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(currentMonth.lengthOfMonth()), 2, RoundingMode.HALF_UP);

        List<MonthlyRevenuePoint> monthly = totals.entrySet().stream()
                .map(entry -> new MonthlyRevenuePoint(entry.getKey().atDay(1), money(entry.getValue())))
                .toList();

        List<DailyRevenuePoint> daily = new ArrayList<>();
        for (int day = 1; day <= currentMonth.lengthOfMonth(); day++) {
            BigDecimal currentCumulative = day <= today.getDayOfMonth()
                    ? totalThroughDay(revenue, currentMonth, day)
                    : null;
            BigDecimal previousCumulative = totalThroughDay(revenue, previousMonth,
                    Math.min(day, previousMonth.lengthOfMonth()));
            daily.add(new DailyRevenuePoint(day,
                    currentCumulative == null ? null : money(currentCumulative), money(previousCumulative)));
        }

        return new SalesDashboardResponse(currencyClient.currentBaseCurrency(), today,
                money(currentTotal), money(previousTotal), money(previousToDate), money(expectedByToday),
                money(variance), performance, progress, monthly, List.copyOf(daily));
    }

    private BigDecimal totalThroughDay(List<InvoiceRevenueEntry> revenue, YearMonth month, int day) {
        return revenue.stream()
                .filter(entry -> YearMonth.from(entry.issueDate()).equals(month))
                .filter(entry -> entry.issueDate().getDayOfMonth() <= day)
                .map(InvoiceRevenueEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
