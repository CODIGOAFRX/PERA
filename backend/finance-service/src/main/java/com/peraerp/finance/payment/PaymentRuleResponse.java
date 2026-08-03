package com.peraerp.finance.payment;
import java.math.BigDecimal;
public record PaymentRuleResponse(int installment,int dueDays,BigDecimal percentage){}
