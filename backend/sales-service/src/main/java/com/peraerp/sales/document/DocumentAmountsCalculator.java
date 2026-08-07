package com.peraerp.sales.document;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DocumentAmountsCalculator {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    public LineAmounts calculate(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercentage,
                                 BigDecimal taxPercentage) {
        requirePositive(quantity, "La cantidad debe ser mayor que cero.");
        requireNonNegative(unitPrice, "El precio unitario no puede ser negativo.");
        requirePercentage(discountPercentage, "El descuento debe estar entre 0 y 100.");
        requirePercentage(taxPercentage, "El impuesto debe estar entre 0 y 100.");
        BigDecimal gross = quantity.multiply(unitPrice);
        BigDecimal discount = gross.multiply(zeroIfNull(discountPercentage)).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(discount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal tax = net.multiply(zeroIfNull(taxPercentage)).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);
        return new LineAmounts(net, tax, net.add(tax).setScale(4, RoundingMode.HALF_UP));
    }

    private BigDecimal zeroIfNull(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(message);
    }

    private void requireNonNegative(BigDecimal value, String message) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(message);
    }

    private void requirePercentage(BigDecimal value, String message) {
        BigDecimal normalized = zeroIfNull(value);
        if (normalized.signum() < 0 || normalized.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException(message);
        }
    }
}
