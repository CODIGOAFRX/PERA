package com.peraerp.sales.verifactu.mapping;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.DocumentAmountsCalculator;
import com.peraerp.sales.document.DocumentLine;
import com.peraerp.sales.document.DocumentType;
import com.peraerp.sales.verifactu.domain.ExemptionCause;
import com.peraerp.sales.verifactu.domain.OperationQualification;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Desglose de IVA del registro de facturación.
 *
 * <p>El registro no lleva las líneas de la factura sino un resumen por combinación fiscal. Agrupar
 * de menos infla el número de entradas y puede pasar del máximo; agrupar de más mezcla situaciones
 * que la AEAT considera distintas —una exportación exenta y una operación con inversión del sujeto
 * pasivo van las dos al 0 %— y eso es declarar mal.</p>
 */
class TaxBreakdownAggregatorTest {

    private final TaxBreakdownAggregator aggregator = new TaxBreakdownAggregator();

    private List<TaxBreakdownEntry> aggregate(List<DocumentLine> lines) {
        return aggregator.aggregate(lines, "01", OperationQualification.SUBJECT_NOT_EXEMPT);
    }

    /**
     * Construye líneas reales pasando por el cálculo de importes de PERA: cantidad 1 y precio igual
     * a la base, de modo que la cuota la calcule el propio ERP y no la prueba.
     */
    private List<DocumentLine> lines(Line... requested) {
        CommercialDocument document = new CommercialDocument(UUID.randomUUID(), "F-1", DocumentType.INVOICE,
                UUID.randomUUID(), "C001", "Cliente", LocalDate.of(2026, 8, 19), null, "EUR", null, null, null);
        for (Line line : requested) {
            DocumentLine documentLine = new DocumentLine(null, null, line.description, BigDecimal.ONE,
                    BigDecimal.ONE, line.base, BigDecimal.ZERO, line.rate, null, null, null, null,
                    null, null, null, null, null);
            documentLine.applyFiscalQualification(line.qualification, line.cause, line.regime);
            document.addLine(documentLine);
        }
        document.recalculate(new DocumentAmountsCalculator());
        return document.getLines();
    }

    private record Line(String description, BigDecimal base, BigDecimal rate,
                        OperationQualification qualification, ExemptionCause cause, String regime) {
    }

    private static Line ordinary(String description, String base, String rate) {
        return new Line(description, new BigDecimal(base), new BigDecimal(rate), null, null, null);
    }

    private static Line qualified(String description, String base, String rate,
                                  OperationQualification qualification, ExemptionCause cause, String regime) {
        return new Line(description, new BigDecimal(base), new BigDecimal(rate), qualification, cause, regime);
    }

    // --- agrupación ---

    @Test
    void linesSharingTheSameTaxRateBecomeASingleEntry() {
        List<TaxBreakdownEntry> breakdown = aggregate(lines(
                ordinary("Vidrio", "100.00", "21"),
                ordinary("Montaje", "200.00", "21")));

        assertThat(breakdown).hasSize(1);
        assertThat(breakdown.get(0).taxableBase()).isEqualByComparingTo("300.00");
        assertThat(breakdown.get(0).taxAmount()).isEqualByComparingTo("63.00");
    }

    @Test
    void eachTaxRateGetsItsOwnEntry() {
        assertThat(aggregate(lines(
                ordinary("General", "100.00", "21"),
                ordinary("Reducido", "100.00", "10"),
                ordinary("Superreducido", "100.00", "4")))).hasSize(3);
    }

    @Test
    void theSameRateWrittenWithDifferentScalesIsStillTheSameRate() {
        List<TaxBreakdownEntry> breakdown = aggregate(lines(
                ordinary("A", "100.00", "21"),
                ordinary("B", "100.00", "21.00"),
                ordinary("C", "100.00", "21.0000")));

        assertThat(breakdown).hasSize(1);
        assertThat(breakdown.get(0).taxableBase()).isEqualByComparingTo("300.00");
    }

    // --- situaciones fiscales al 0 % ---

    @Test
    void exemptOperationsWithDifferentCausesAreNotMerged() {
        List<TaxBreakdownEntry> breakdown = aggregate(lines(
                qualified("Exportación", "500.00", "0", OperationQualification.EXEMPT, ExemptionCause.ARTICLE_21, "01"),
                qualified("Formación", "300.00", "0", OperationQualification.EXEMPT, ExemptionCause.ARTICLE_20, "01")));

        assertThat(breakdown).hasSize(2);
    }

    @Test
    void exemptOperationsSharingCauseAreMerged() {
        List<TaxBreakdownEntry> breakdown = aggregate(lines(
                qualified("Exportación 1", "500.00", "0", OperationQualification.EXEMPT, ExemptionCause.ARTICLE_21, "01"),
                qualified("Exportación 2", "100.00", "0", OperationQualification.EXEMPT, ExemptionCause.ARTICLE_21, "01")));

        assertThat(breakdown).hasSize(1);
        assertThat(breakdown.get(0).taxableBase()).isEqualByComparingTo("600.00");
        assertThat(breakdown.get(0).exemptionCause()).isEqualTo(ExemptionCause.ARTICLE_21);
    }

    @Test
    void exemptNotSubjectAndReverseChargeAreThreeDifferentThingsAtZeroPercent() {
        List<TaxBreakdownEntry> breakdown = aggregate(lines(
                qualified("Exenta", "100.00", "0", OperationQualification.EXEMPT, ExemptionCause.ARTICLE_20, "01"),
                qualified("No sujeta", "100.00", "0", OperationQualification.NOT_SUBJECT, null, "01"),
                qualified("Inversión", "100.00", "0", OperationQualification.REVERSE_CHARGE, null, "01")));

        assertThat(breakdown).hasSize(3);
    }

    @Test
    void adifferentRegimeKeySplitsTheEntry() {
        assertThat(aggregate(lines(
                qualified("General", "100.00", "21", OperationQualification.SUBJECT_NOT_EXEMPT, null, "01"),
                qualified("Recargo", "100.00", "21", OperationQualification.SUBJECT_NOT_EXEMPT, null, "02"))))
                .hasSize(2);
    }

    // --- valores por defecto de la empresa ---

    @Test
    void linesWithoutQualificationInheritTheCompanyDefaults() {
        List<TaxBreakdownEntry> breakdown = aggregator.aggregate(lines(ordinary("Vidrio", "100.00", "21")),
                "03", OperationQualification.SUBJECT_NOT_EXEMPT);

        assertThat(breakdown.get(0).regimeKey()).isEqualTo("03");
        assertThat(breakdown.get(0).qualification()).isEqualTo(OperationQualification.SUBJECT_NOT_EXEMPT);
        assertThat(breakdown.get(0).exemptionCause()).isNull();
    }

    // --- redondeo ---

    @Test
    void amountsAreRoundedOnceTheLinesAreAddedUpNotBefore() {
        List<TaxBreakdownEntry> breakdown = aggregate(lines(
                ordinary("A", "33.3333", "21"),
                ordinary("B", "33.3333", "21"),
                ordinary("C", "33.3334", "21")));

        assertThat(breakdown.get(0).taxableBase()).isEqualByComparingTo("100.00");
    }

    // --- límites ---

    @Test
    void theBreakdownCannotExceedTwelveEntries() {
        List<Line> thirteen = new ArrayList<>();
        for (int rate = 0; rate < 13; rate++) {
            thirteen.add(ordinary("Línea " + rate, "10.00", String.valueOf(rate)));
        }

        assertThatThrownBy(() -> aggregate(lines(thirteen.toArray(new Line[0]))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("12");
    }

    @Test
    void twelveEntriesAreStillAccepted() {
        List<Line> twelve = new ArrayList<>();
        for (int rate = 0; rate < 12; rate++) {
            twelve.add(ordinary("Línea " + rate, "10.00", String.valueOf(rate)));
        }

        assertThat(aggregate(lines(twelve.toArray(new Line[0])))).hasSize(12);
    }

    @Test
    void anInvoiceWithoutLinesCannotProduceABreakdown() {
        assertThatThrownBy(() -> aggregate(List.of()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void anExemptLineWithoutACauseStopsTheIssuanceInsteadOfGuessing() {
        assertThatThrownBy(() -> aggregate(lines(
                qualified("Sin causa", "100.00", "0", OperationQualification.EXEMPT, null, "01"))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("causa de exención");
    }

    // --- reproducibilidad ---

    @Test
    void theSameInvoiceAlwaysProducesTheBreakdownInTheSameOrder() {
        List<DocumentLine> invoice = lines(
                ordinary("General", "100.00", "21"),
                ordinary("Reducido", "100.00", "10"),
                ordinary("Superreducido", "100.00", "4"));

        assertThat(aggregate(invoice)).isEqualTo(aggregate(invoice));
    }
}
