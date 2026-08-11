package com.peraerp.sales.dashboard;

import java.math.BigDecimal;

public record DailyRevenuePoint(int day, BigDecimal currentCumulative, BigDecimal previousCumulative) {
}
