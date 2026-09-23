package com.peraerp.sales.verifactu;

import com.peraerp.sales.document.*;
import com.peraerp.sales.verifactu.chain.RecordPayloadFactory;
import com.peraerp.sales.verifactu.domain.VerifactuSettings;
import com.peraerp.sales.verifactu.domain.VerifactuSettingsRepository;
import com.peraerp.sales.verifactu.mapping.TaxBreakdownAggregator;
import com.peraerp.sales.verifactu.xml.RegistroAltaXmlWriter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class InvoiceRoundingTest {
    private CommercialDocument invoice(String currency, String rate, String... basesAndRates) {
        CommercialDocument invoice = new CommercialDocument(UUID.randomUUID(), "FAC-TEST-1", DocumentType.INVOICE,
                UUID.randomUUID(), "C003", "Cliente de prueba", LocalDate.of(2026, 9, 23), null,
                currency, null, null, null);
        for (int i = 0; i < basesAndRates.length; i += 2) {
            invoice.addLine(new DocumentLine(null, "P1", "Producto", BigDecimal.ONE,
                    new BigDecimal(basesAndRates[i]), BigDecimal.ZERO, new BigDecimal(basesAndRates[i + 1])));
        }
        invoice.recalculate(new DocumentAmountsCalculator());
        invoice.applyCurrencySnapshot("EUR", new BigDecimal(rate), invoice.getIssueDate(), "TEST");
        invoice.confirm();
        return invoice;
    }

    private String xml(CommercialDocument invoice) {
        var settings = new VerifactuSettings(invoice.getCompanyId(), "89890001K", "Empresa de pruebas",
                "PERA", "01", "1.0", "89890001K");
        var factory = new VerifactuInvoicePayloadFactory(new TaxBreakdownAggregator(), new RegistroAltaXmlWriter(),
                mock(VerifactuSettingsRepository.class), "TEST-INSTALL", "Productor de pruebas");
        return factory.forInvoice(invoice, settings).serialize(new RecordPayloadFactory.PayloadContext(
                null, "A".repeat(64), ZonedDateTime.parse("2026-09-23T10:00:00+02:00[Europe/Madrid]"), null));
    }

    @Test
    void serializesTheReportedDeliveryNoteAmounts() {
        var invoice = invoice("EUR", "1", "55.90", "21");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("11.74");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("67.64");
        assertThat(xml(invoice)).contains("<sf:CuotaTotal>11.74</sf:CuotaTotal>",
                "<sf:ImporteTotal>67.64</sf:ImporteTotal>");
    }

    @Test
    void roundsNetAndTaxBeforeAddingThePayableTotal() {
        var invoice = invoice("EUR", "1", "53.973", "21");
        assertThat(invoice.getNetAmount()).isEqualByComparingTo("53.97");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("11.33");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("65.30");
        assertThat(xml(invoice)).contains("<sf:ImporteTotal>65.30</sf:ImporteTotal>");
    }

    @Test
    void severalFiscalGroupsKeepTheirRoundingConsistentWithTheInvoice() {
        var invoice = invoice("EUR", "1", "0.03", "21", "0.06", "10");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("0.01");
        assertThat(xml(invoice)).contains("<sf:CuotaTotal>0.01</sf:CuotaTotal>",
                "<sf:ImporteTotal>0.10</sf:ImporteTotal>");
    }

    @Test
    void convertsTheBreakdownToEurosBeforeRounding() {
        var invoice = invoice("USD", "0.86", "55.90", "21");
        assertThat(invoice.getBaseNetAmount()).isEqualByComparingTo("48.07");
        assertThat(invoice.getBaseTaxAmount()).isEqualByComparingTo("10.10");
        assertThat(xml(invoice)).contains("<sf:ImporteTotal>58.17</sf:ImporteTotal>");
    }

    @Test
    void keepsSubCentLinePrecisionUntilAllLinesHaveBeenSummed() {
        var invoice = invoice("EUR", "1", "33.3333", "21", "33.3333", "21", "33.3334", "21");
        assertThat(invoice.getNetAmount()).isEqualByComparingTo("100.00");
        assertThat(xml(invoice)).contains("<sf:ImporteTotal>121.00</sf:ImporteTotal>");
    }
}
