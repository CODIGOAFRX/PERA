package com.peraerp.sales.document;
import java.math.BigDecimal;
public record LineAmounts(BigDecimal net, BigDecimal tax, BigDecimal total) {}
