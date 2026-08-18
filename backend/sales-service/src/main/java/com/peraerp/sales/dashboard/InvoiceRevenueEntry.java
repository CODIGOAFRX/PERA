package com.peraerp.sales.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvoiceRevenueEntry(LocalDate issueDate, BigDecimal amount) {
}
