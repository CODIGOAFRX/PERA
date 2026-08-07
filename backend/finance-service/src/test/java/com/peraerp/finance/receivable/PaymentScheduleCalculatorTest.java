package com.peraerp.finance.receivable;

import com.peraerp.finance.payment.PaymentMethod;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentScheduleCalculatorTest {
    @Test
    void assignsRoundingRemainderToLastInstallment() {
        PaymentMethod method=new PaymentMethod(UUID.randomUUID(),"30-60-90","Tres vencimientos");
        method.addRule(1,30,new BigDecimal("33.3333")); method.addRule(2,60,new BigDecimal("33.3333"));
        method.addRule(3,90,new BigDecimal("33.3334"));
        var result=new PaymentScheduleCalculator().calculate(new BigDecimal("100.00"),LocalDate.of(2026,8,3),method.getRules());
        assertThat(result).extracting(ScheduleItem::amount).containsExactly(new BigDecimal("33.3333"),new BigDecimal("33.3333"),new BigDecimal("33.3334"));
        assertThat(result).extracting(ScheduleItem::dueDate).containsExactly(LocalDate.of(2026,9,2),LocalDate.of(2026,10,2),LocalDate.of(2026,11,1));
    }

    @Test
    void rejectsEmptySchedulesAndNonPositiveTotals() {
        PaymentScheduleCalculator calculator=new PaymentScheduleCalculator();
        assertThatThrownBy(()->calculator.calculate(BigDecimal.TEN,LocalDate.now(),java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->calculator.calculate(BigDecimal.ZERO,LocalDate.now(),java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
