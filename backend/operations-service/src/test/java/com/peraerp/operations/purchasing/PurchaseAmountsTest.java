package com.peraerp.operations.purchasing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseAmountsTest {

    private static final UUID COMPANY = UUID.randomUUID();
    private static final UUID DOCUMENT = UUID.randomUUID();

    @Test
    void roundsTheLineBaseToCentsAfterApplyingTheDiscount() {
        assertThat(PurchaseAmounts.lineNet(new BigDecimal("3"), new BigDecimal("9.995"), BigDecimal.ZERO))
                .isEqualByComparingTo("29.99");
        assertThat(PurchaseAmounts.lineNet(new BigDecimal("2"), new BigDecimal("27.95"), new BigDecimal("10")))
                .isEqualByComparingTo("50.31");
    }

    @Test
    void calculatesTheTaxOnTheSumOfBasesOfEachRateLikeTheSupplierInvoiceDoes() {
        // Línea a línea serían 0,01 × 3 = 0,03 de cuota; sobre la base conjunta, 0,09 × 21 % = 0,02.
        List<PurchaseDocumentLine> lines = List.of(line("0.03", "21"), line("0.03", "21.0000"), line("0.03", "21"));

        PurchaseAmounts.Totals totals = PurchaseAmounts.totals(lines);

        assertThat(totals.net()).isEqualByComparingTo("0.09");
        assertThat(totals.tax()).isEqualByComparingTo("0.02");
        assertThat(totals.total()).isEqualByComparingTo("0.11");
    }

    @Test
    void keepsEachTaxRateInItsOwnGroup() {
        PurchaseAmounts.Totals totals = PurchaseAmounts.totals(
                List.of(line("55.90", "21"), line("10.00", "10"), line("5.00", "0")));

        assertThat(totals.net()).isEqualByComparingTo("70.90");
        assertThat(totals.tax()).isEqualByComparingTo("12.74");
        assertThat(totals.total()).isEqualByComparingTo("83.64");
    }

    @Test
    void derivesTheWarehouseUnitCostFromTheDiscountedBase() {
        PurchaseDocumentLine line = new PurchaseDocumentLine(COMPANY, DOCUMENT, 1, UUID.randomUUID(), "P-1",
                "Producto", "UNIT", new BigDecimal("3"), new BigDecimal("10"), new BigDecimal("10"),
                new BigDecimal("21"), new BigDecimal("27.00"));

        assertThat(PurchaseAmounts.unitCost(line)).isEqualByComparingTo("9");
    }

    private PurchaseDocumentLine line(String net, String taxPercentage) {
        return new PurchaseDocumentLine(COMPANY, DOCUMENT, 1, null, null, "Línea", "UNIT", BigDecimal.ONE,
                new BigDecimal(net), BigDecimal.ZERO, new BigDecimal(taxPercentage), new BigDecimal(net));
    }
}
