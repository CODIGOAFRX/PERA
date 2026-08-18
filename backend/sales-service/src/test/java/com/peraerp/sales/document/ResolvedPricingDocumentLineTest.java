package com.peraerp.sales.document;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResolvedPricingDocumentLineTest {
    @Test
    void calculatesFromExactResolvedTotalThenAppliesManualDiscountAndTax() {
        DocumentLine line = new DocumentLine(UUID.randomUUID(), "P-1", "Producto",
                new BigDecimal("3"), new BigDecimal("4"), new BigDecimal("47.5000"),
                new BigDecimal("10"), new BigDecimal("21"), UUID.randomUUID(), "PROMO",
                new BigDecimal("190.0000"), "{}");

        line.recalculate(new DocumentAmountsCalculator());

        assertThat(line.getNetAmount()).isEqualByComparingTo("171.0000");
        assertThat(line.getTaxAmount()).isEqualByComparingTo("35.9100");
        assertThat(line.getTotalAmount()).isEqualByComparingTo("206.9100");
        assertThat(line.getRequestedQuantity()).isEqualByComparingTo("3");
        assertThat(line.getQuantity()).isEqualByComparingTo("4");
    }

    @Test
    void convertedLinesKeepTheCompleteTaxSnapshot() {
        UUID taxCodeId = UUID.randomUUID();
        DocumentLine original = new DocumentLine(UUID.randomUUID(), "P-1", "Producto",
                BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ZERO,
                new BigDecimal("21"), null, null, null, null, taxCodeId, "IVA21", "ES",
                "IVA general", false);

        DocumentLine copy = original.copySnapshot();

        assertThat(copy.getTaxCodeId()).isEqualTo(taxCodeId);
        assertThat(copy.getTaxCodeSnapshot()).isEqualTo("IVA21");
        assertThat(copy.getTaxCountryCodeSnapshot()).isEqualTo("ES");
        assertThat(copy.getTaxNameSnapshot()).isEqualTo("IVA general");
        assertThat(copy.getTaxExemptSnapshot()).isFalse();
    }
}
