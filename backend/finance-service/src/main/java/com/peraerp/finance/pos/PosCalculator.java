package com.peraerp.finance.pos;

import com.peraerp.platform.domain.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cálculo de importes del TPV. No depende de la base de datos para poder probarlo a fondo.
 *
 * <p>Los precios de tienda llevan impuestos incluidos. Cada línea se redondea a céntimos tras su
 * descuento y tras el descuento del ticket; el total es la suma de líneas. La base y la cuota se
 * obtienen por tipo impositivo a partir de ese total, como en una factura simplificada: así la
 * suma de base y cuota coincide siempre con lo cobrado.</p>
 */
public final class PosCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record LineInput(UUID productId, String productCode, String productName, String barcode,
                            String unitOfMeasure, BigDecimal quantity, BigDecimal unitPrice, boolean priceOverridden,
                            BigDecimal discountPercent, BigDecimal taxRate) {
    }

    public record TaxLine(BigDecimal rate, BigDecimal base, BigDecimal tax, BigDecimal total) {
    }

    public record Totals(List<BigDecimal> lineTotals, BigDecimal grossAmount, BigDecimal discountAmount,
                         BigDecimal totalAmount, BigDecimal taxBaseAmount, BigDecimal taxAmount,
                         List<TaxLine> taxes) {
    }

    public record Payment(PosPaymentMethod method, BigDecimal cashAmount, BigDecimal cardAmount,
                          BigDecimal cashTendered, BigDecimal changeAmount) {
    }

    private PosCalculator() {
    }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Precio de venta al público: precio base del catálogo más su impuesto. */
    public static BigDecimal retailPrice(BigDecimal basePrice, BigDecimal taxRate) {
        return money(basePrice.multiply(HUNDRED.add(taxRate)).divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    public static Totals saleTotals(List<LineInput> lines, BigDecimal ticketDiscountPercent) {
        if (lines.isEmpty()) {
            throw new BusinessRuleException("El ticket no tiene ninguna línea.");
        }
        BigDecimal ticketFactor = HUNDRED.subtract(ticketDiscountPercent);
        List<BigDecimal> lineTotals = new ArrayList<>();
        List<BigDecimal> rates = new ArrayList<>();
        BigDecimal gross = BigDecimal.ZERO;
        for (LineInput line : lines) {
            BigDecimal lineGross = money(line.quantity().multiply(line.unitPrice()));
            BigDecimal afterLineDiscount = applyPercent(lineGross, HUNDRED.subtract(line.discountPercent()));
            lineTotals.add(applyPercent(afterLineDiscount, ticketFactor));
            rates.add(line.taxRate());
            gross = gross.add(lineGross);
        }
        return totals(lineTotals, rates, gross);
    }

    /** Totales de una devolución: cada línea devuelve el importe que se cobró por esas unidades. */
    public static Totals refundTotals(List<BigDecimal> lineTotals, List<BigDecimal> rates) {
        BigDecimal gross = lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return totals(lineTotals, rates, gross);
    }

    public static List<TaxLine> taxBreakdown(List<BigDecimal> lineTotals, List<BigDecimal> rates) {
        Map<BigDecimal, BigDecimal> byRate = new TreeMap<>();
        for (int index = 0; index < lineTotals.size(); index++) {
            BigDecimal rate = rates.get(index).stripTrailingZeros();
            // 21,0000 y 21 son el mismo tipo; sin escala negativa para no publicar «1E+1» en vez de 10.
            byRate.merge(rate.scale() < 0 ? rate.setScale(0) : rate, lineTotals.get(index), BigDecimal::add);
        }
        List<TaxLine> taxes = new ArrayList<>();
        byRate.forEach((rate, total) -> {
            BigDecimal base = money(total.multiply(HUNDRED).divide(HUNDRED.add(rate), 8, RoundingMode.HALF_UP));
            taxes.add(new TaxLine(rate, base, total.subtract(base), total));
        });
        return taxes;
    }

    /**
     * Reparte el cobro. En efectivo se puede entregar más y se devuelve el cambio; con tarjeta se
     * cobra el total exacto; en mixto la tarjeta cubre una parte y el resto se paga en efectivo.
     */
    public static Payment payment(PosPaymentMethod method, BigDecimal total, BigDecimal cashTendered,
                                  BigDecimal cardAmount) {
        return switch (method) {
            case CARD -> new Payment(method, BigDecimal.ZERO, total, null, BigDecimal.ZERO);
            case CASH -> cash(method, total, BigDecimal.ZERO, cashTendered);
            case MIXED -> {
                if (cardAmount == null || cardAmount.signum() <= 0 || cardAmount.compareTo(total) >= 0) {
                    throw new BusinessRuleException("En un cobro mixto la parte con tarjeta debe ser mayor que cero "
                            + "y menor que el total.");
                }
                yield cash(method, total, money(cardAmount), cashTendered);
            }
        };
    }

    private static Payment cash(PosPaymentMethod method, BigDecimal total, BigDecimal card, BigDecimal tendered) {
        BigDecimal cashDue = total.subtract(card);
        BigDecimal given = tendered == null ? cashDue : money(tendered);
        if (given.compareTo(cashDue) < 0) {
            throw new BusinessRuleException("El efectivo entregado (" + given.toPlainString()
                    + ") no cubre lo que hay que cobrar en efectivo (" + cashDue.toPlainString() + ").");
        }
        return new Payment(method, cashDue, card, given, given.subtract(cashDue));
    }

    private static Totals totals(List<BigDecimal> lineTotals, List<BigDecimal> rates, BigDecimal gross) {
        List<TaxLine> taxes = taxBreakdown(lineTotals, rates);
        BigDecimal total = lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal base = taxes.stream().map(TaxLine::base).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Totals(List.copyOf(lineTotals), gross, gross.subtract(total), total, base, total.subtract(base),
                taxes);
    }

    private static BigDecimal applyPercent(BigDecimal amount, BigDecimal remainingPercent) {
        return money(amount.multiply(remainingPercent).divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }
}
