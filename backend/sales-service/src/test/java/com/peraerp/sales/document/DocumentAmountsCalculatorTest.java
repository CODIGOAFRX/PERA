package com.peraerp.sales.document;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentAmountsCalculatorTest {
    private final DocumentAmountsCalculator calculator = new DocumentAmountsCalculator();

    @Test
    void calculatesDiscountAndTaxWithoutBinaryRoundingErrors() {
        LineAmounts result = calculator.calculate(new BigDecimal("3"), new BigDecimal("19.99"),
                new BigDecimal("10"), new BigDecimal("21"));
        assertThat(result.net()).isEqualByComparingTo("53.9730");
        assertThat(result.tax()).isEqualByComparingTo("11.3343");
        assertThat(result.total()).isEqualByComparingTo("65.3073");
    }

    @Test
    void rejectsInvalidAmountsBeforeTheyReachPersistence() {
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.ZERO, BigDecimal.TEN,
                BigDecimal.ZERO, new BigDecimal("21"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculate(BigDecimal.ONE, BigDecimal.TEN,
                new BigDecimal("101"), new BigDecimal("21"))).isInstanceOf(IllegalArgumentException.class);
    }
}
