package com.peraerp.finance.payment;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record PaymentRuleRequest(@Min(0) int dueDays,@NotNull @DecimalMin(value="0",inclusive=false) BigDecimal percentage){}
