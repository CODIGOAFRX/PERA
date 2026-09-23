package com.peraerp.sales.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Keeps rounded components consistent with the rounded sum, without losing line precision. */
public final class MonetaryRounding {
    private static final BigDecimal CENT = new BigDecimal("0.01");

    private MonetaryRounding() {}

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static List<BigDecimal> distribute(List<BigDecimal> exact) {
        List<BigDecimal> rounded = new ArrayList<>(exact.stream().map(MonetaryRounding::round).toList());
        BigDecimal target = round(exact.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal difference = target.subtract(rounded.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        while (difference.signum() != 0) {
            int direction = difference.signum();
            int best = 0;
            for (int i = 1; i < exact.size(); i++) {
                BigDecimal remainder = exact.get(i).subtract(rounded.get(i));
                BigDecimal bestRemainder = exact.get(best).subtract(rounded.get(best));
                if (remainder.compareTo(bestRemainder) * direction > 0) best = i;
            }
            BigDecimal adjustment = CENT.multiply(BigDecimal.valueOf(direction));
            rounded.set(best, rounded.get(best).add(adjustment));
            difference = difference.subtract(adjustment);
        }
        return List.copyOf(rounded);
    }
}
