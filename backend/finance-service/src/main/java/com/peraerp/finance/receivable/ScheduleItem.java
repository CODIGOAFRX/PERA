package com.peraerp.finance.receivable;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ScheduleItem(int installment,LocalDate dueDate,BigDecimal amount){}
