package com.peraerp.sales.commission;

import com.peraerp.sales.document.MonetaryRounding;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Elige el porcentaje de comisión de cada línea, con el mismo criterio que DimproCristalWin:
 * gana la regla más concreta.
 *
 * <ol>
 *   <li>Primero las reglas del artículo de la línea, después las de su grupo y después las generales.</li>
 *   <li>Entre las del mismo nivel, la de tramo de importe más estrecho (una sin tramo es la más ancha).</li>
 *   <li>El tramo se compara con el importe neto de la línea en valor absoluto, para que un abono caiga en
 *       el mismo tramo que la venta que corrige.</li>
 *   <li>Si ninguna regla encaja, la comisión por defecto del comercial; si no tiene, cero.</li>
 * </ol>
 */
public final class CommissionCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Línea de factura a comisionar: importe neto en moneda base, ya con el signo del documento. */
    public record Line(int order, String description, BigDecimal baseAmount, UUID productId, UUID productGroupId) {}

    private CommissionCalculator() {}

    public static SalesCommissionLine calculate(Line line, List<CommissionRule> rules, BigDecimal defaultPercentage) {
        Optional<CommissionRule> rule = rules.stream()
                .filter(CommissionRule::isActive)
                .filter(candidate -> applies(candidate, line))
                .min(Comparator.comparingInt(CommissionCalculator::level).thenComparing(CommissionCalculator::width));
        BigDecimal percentage;
        SalesCommissionLine.Origin origin;
        if (rule.isPresent()) {
            percentage = rule.get().getPercentage();
            origin = SalesCommissionLine.Origin.RULE;
        } else if (defaultPercentage != null) {
            percentage = defaultPercentage;
            origin = SalesCommissionLine.Origin.DEFAULT;
        } else {
            percentage = BigDecimal.ZERO;
            origin = SalesCommissionLine.Origin.NONE;
        }
        BigDecimal commission = MonetaryRounding.round(line.baseAmount().multiply(percentage).divide(HUNDRED));
        return new SalesCommissionLine(line.order(), line.description(), line.baseAmount(), percentage, commission,
                rule.map(CommissionRule::getId).orElse(null), origin);
    }

    private static boolean applies(CommissionRule rule, Line line) {
        if (rule.getProductId() != null && !rule.getProductId().equals(line.productId())) {
            return false;
        }
        if (rule.getProductGroupId() != null && !rule.getProductGroupId().equals(line.productGroupId())) {
            return false;
        }
        BigDecimal amount = line.baseAmount().abs();
        return (rule.getAmountFrom() == null || amount.compareTo(rule.getAmountFrom()) >= 0)
                && (rule.getAmountTo() == null || amount.compareTo(rule.getAmountTo()) <= 0);
    }

    /** 0 artículo, 1 grupo, 2 general. */
    private static int level(CommissionRule rule) {
        return rule.getProductId() != null ? 0 : rule.getProductGroupId() != null ? 1 : 2;
    }

    private static BigDecimal width(CommissionRule rule) {
        if (rule.getAmountFrom() == null && rule.getAmountTo() == null) {
            return new BigDecimal("1E30");
        }
        BigDecimal from = rule.getAmountFrom() == null ? BigDecimal.ZERO : rule.getAmountFrom();
        BigDecimal to = rule.getAmountTo() == null ? new BigDecimal("1E29") : rule.getAmountTo();
        return to.subtract(from);
    }
}
