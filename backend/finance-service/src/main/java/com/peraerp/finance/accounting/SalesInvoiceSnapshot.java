package com.peraerp.finance.accounting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalesInvoiceSnapshot(UUID id, String number, String type, String status, LocalDate issueDate,
                                   String customerCode, String customerName, String baseCurrency,
                                   BigDecimal baseNetAmount, BigDecimal baseTaxAmount, BigDecimal baseTotalAmount) {}
