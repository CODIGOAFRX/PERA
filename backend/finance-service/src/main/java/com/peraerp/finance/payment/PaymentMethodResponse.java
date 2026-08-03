package com.peraerp.finance.payment;
import java.util.*;
public record PaymentMethodResponse(UUID id,String code,String name,boolean active,List<PaymentRuleResponse> rules){
    static PaymentMethodResponse from(PaymentMethod method){return new PaymentMethodResponse(method.getId(),method.getCode(),method.getName(),method.isActive(),method.getRules().stream().map(r->new PaymentRuleResponse(r.getInstallmentNumber(),r.getDueDays(),r.getPercentage())).toList());}
}
