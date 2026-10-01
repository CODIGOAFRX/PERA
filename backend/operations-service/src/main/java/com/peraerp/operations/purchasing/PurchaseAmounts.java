package com.peraerp.operations.purchasing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cálculo de importes de un documento de compra.
 *
 * <p>La base de cada línea se redondea al céntimo. La cuota no se calcula línea a línea sino sobre
 * la suma de bases de cada tipo impositivo, que es como la calcula el proveedor en su factura: así
 * el total registrado coincide con el del documento recibido.</p>
 */
public final class PurchaseAmounts {

    private static final int MONEY_SCALE = 2;
    private static final int UNIT_COST_SCALE = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Totals(BigDecimal net, BigDecimal tax, BigDecimal total) {
    }

    private PurchaseAmounts() {
    }

    public static BigDecimal lineNet(BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPercentage) {
        BigDecimal gross = quantity.multiply(unitPrice);
        BigDecimal discount = gross.multiply(discountPercentage).divide(HUNDRED);
        return gross.subtract(discount).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static Totals totals(List<PurchaseDocumentLine> lines) {
        // TreeMap compara con compareTo: 21 y 21.0000 son el mismo tipo impositivo.
        Map<BigDecimal, BigDecimal> baseByRate = new TreeMap<>();
        for (PurchaseDocumentLine line : lines) {
            baseByRate.merge(line.getTaxPercentage(), line.getNetAmount(), BigDecimal::add);
        }
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (Map.Entry<BigDecimal, BigDecimal> entry : baseByRate.entrySet()) {
            net = net.add(entry.getValue());
            tax = tax.add(entry.getValue().multiply(entry.getKey()).divide(HUNDRED)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        }
        net = net.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Totals(net, tax, net.add(tax));
    }

    /** Coste unitario de entrada en almacén: base de la línea, ya con descuento, entre la cantidad. */
    public static BigDecimal unitCost(PurchaseDocumentLine line) {
        return line.getNetAmount().divide(line.getQuantity(), UNIT_COST_SCALE, RoundingMode.HALF_UP);
    }
}
