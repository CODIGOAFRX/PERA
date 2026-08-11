package com.peraerp.sales.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesDashboardResponse(
        String currency,
        LocalDate asOfDate,
        BigDecimal currentMonthTotal,
        BigDecimal previousMonthTotal,
        BigDecimal previousMonthToDate,
        BigDecimal expectedByToday,
        BigDecimal varianceAmount,
        BigDecimal performancePercentage,
        BigDecimal monthProgressPercentage,
        List<MonthlyRevenuePoint> monthlyRevenue,
        List<DailyRevenuePoint> dailyRevenue
) {
}
