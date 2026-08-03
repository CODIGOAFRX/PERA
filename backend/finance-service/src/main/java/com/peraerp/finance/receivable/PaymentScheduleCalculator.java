package com.peraerp.finance.receivable;
import com.peraerp.finance.payment.PaymentScheduleRule;
import org.springframework.stereotype.Component;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
@Component
public class PaymentScheduleCalculator {
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    public List<ScheduleItem> calculate(BigDecimal total,LocalDate issueDate,List<PaymentScheduleRule> rules){
        List<ScheduleItem> result=new ArrayList<>(); BigDecimal assigned=BigDecimal.ZERO;
        for(int i=0;i<rules.size();i++){
            PaymentScheduleRule rule=rules.get(i);
            BigDecimal amount=i==rules.size()-1?total.subtract(assigned):total.multiply(rule.getPercentage()).divide(HUNDRED,4,RoundingMode.HALF_UP);
            assigned=assigned.add(amount); result.add(new ScheduleItem(rule.getInstallmentNumber(),issueDate.plusDays(rule.getDueDays()),amount));
        }
        return List.copyOf(result);
    }
}
