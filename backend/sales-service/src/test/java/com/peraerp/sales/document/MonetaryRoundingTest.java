package com.peraerp.sales.document;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MonetaryRoundingTest {
    @Test
    void distributedAmountsAlwaysSumToTheRoundedTotalAndStayWithinOneCent() {
        Random random = new Random(15432);
        for (int example = 0; example < 1000; example++) {
            List<BigDecimal> exact = IntStream.range(0, 12)
                    .mapToObj(i -> BigDecimal.valueOf(random.nextInt(200000) - 100000, 4)).toList();
            List<BigDecimal> rounded = MonetaryRounding.distribute(exact);
            assertThat(rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(MonetaryRounding.round(exact.stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
            for (int i = 0; i < exact.size(); i++) {
                assertThat(rounded.get(i).scale()).isEqualTo(2);
                assertThat(rounded.get(i).subtract(exact.get(i)).abs()).isLessThan(new BigDecimal("0.01"));
            }
        }
        assertThat(MonetaryRounding.distribute(List.of())).isEmpty();
    }
}
