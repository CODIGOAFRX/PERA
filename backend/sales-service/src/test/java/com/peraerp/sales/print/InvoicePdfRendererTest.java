package com.peraerp.sales.print;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Maqueta de la factura en PDF.
 *
 * <p>Un PDF no se puede comprobar mirándolo desde una prueba, así que se comprueba lo que sí es
 * verificable: que el fichero es un PDF, cuántas hojas tiene y qué texto lleva impreso. Eso cubre
 * lo que de verdad puede fallar sin que nadie se dé cuenta —una leyenda que aparece donde no debe,
 * una factura larga que se corta— y deja fuera lo que solo se juzga con el ojo.</p>
 */
class InvoicePdfRendererTest {

    private static final String LEGEND = "Factura verificable en la sede electrónica de la AEAT";
    private static final String FINGERPRINT =
            "ABAB978740EC1848D4044B356D7CC14CE96844D2FAA08C273EA837045A8557D1";

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    private InvoicePdfContent.Issuer issuer() {
        return new InvoicePdfContent.Issuer("EMPRESA DE PRUEBAS S.L.", "89890001K",
                "Calle Mayor, 1", null, "14008", "Córdoba", "Córdoba", "957000000",
                "facturacion@ejemplo.es", "https://ejemplo.es");
    }

    private InvoicePdfContent.Recipient recipient() {
        return new InvoicePdfContent.Recipient("ALUMINIOS FAMA S.L.", "B75777847", "C001",
                "Domicilio pendiente: PERA todavía no guarda la dirección de los clientes.");
    }

    private List<InvoicePdfContent.Line> lines(int count) {
        List<InvoicePdfContent.Line> lines = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            lines.add(new InvoicePdfContent.Line(index, "P" + index, "Vidrio laminado 6+6",
                    new BigDecimal("2.00"), new BigDecimal("140.00"), BigDecimal.ZERO,
                    new BigDecimal("21.00"), new BigDecimal("280.00")));
        }
        return lines;
    }

    private InvoicePdfContent content(int lineCount, InvoicePdfContent.Verifactu verifactu) {
        List<InvoicePdfContent.TaxRow> taxes = List.of(new InvoicePdfContent.TaxRow(
                new BigDecimal("21"), new BigDecimal("280.00"), new BigDecimal("58.80")));
        return new InvoicePdfContent(issuer(), recipient(), "Factura", "FAC-2026-000002",
                LocalDate.of(2026, 8, 19), LocalDate.of(2026, 9, 19), "F1", null, null, "EUR",
                lines(lineCount), taxes, new BigDecimal("280.00"), new BigDecimal("58.80"),
                new BigDecimal("338.80"), "Transferencia", null, verifactu, null);
    }

    private InvoicePdfContent.Verifactu verifactu() {
        return new InvoicePdfContent.Verifactu(
                "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR?nif=89890001K&numserie=FAC-2026-000002"
                        + "&fecha=19-08-2026&importe=338.80",
                FINGERPRINT, "https://prewww2.aeat.es/wlpl/TIKE-CONT/ValidarQR");
    }

    private String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private int pagesOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void producesARealPdf() {
        byte[] pdf = renderer.render(content(1, verifactu()));

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    @Test
    void printsTheInvoiceIdentityAndTheParties() throws IOException {
        String text = textOf(renderer.render(content(1, verifactu())));

        assertThat(text).contains("FAC-2026-000002", "19/08/2026", "EMPRESA DE PRUEBAS S.L.",
                "89890001K", "ALUMINIOS FAMA S.L.", "B75777847");
    }

    /**
     * Los importes se imprimen con la coma decimal española. Un separador anglosajón en una factura
     * es de las cosas que nadie mira dos veces y que dejan el documento mal.
     */
    @Test
    void printsAmountsInSpanishNotation() throws IOException {
        String text = textOf(renderer.render(content(1, verifactu())));

        assertThat(text).contains("338,80");
        // El QR sí lleva el importe con punto, pero es una imagen: en el texto no debe aparecer.
        assertThat(text).doesNotContain("338.80");
    }

    /**
     * El domicilio del destinatario todavía no existe en PERA, y el art. 6.1.e) del RD 1619/2012 lo
     * exige. Mientras falte, la factura tiene que decirlo: una que parece completa sin estarlo es
     * peor que una que avisa. Cuando se implemente, esta prueba se cae y hay que cambiarla.
     */
    @Test
    void warnsThatTheRecipientAddressIsStillMissing() throws IOException {
        String text = textOf(renderer.render(content(1, verifactu())));

        assertThat(text).contains("Domicilio pendiente");
    }

    /**
     * La leyenda se busca entera sobre el texto con los espacios normalizados, porque va partida en
     * varias líneas junto al QR.
     *
     * <p>La versión anterior de esta prueba buscaba solo el principio de la frase, y pasó mientras
     * la factura imprimía «…en la sede electrónica de» sin «la AEAT»: el maquetador tiraba lo que
     * no cabía en las líneas disponibles. Una prueba que mira un prefijo no ve que falte el final.</p>
     */
    @Test
    void printsTheWholeVerifactuLegendAndTheFingerprint() throws IOException {
        String text = textOf(renderer.render(content(1, verifactu())));

        assertThat(text).contains("VERI*FACTU").contains(FINGERPRINT);
        assertThat(text.replaceAll("\\s+", " ")).contains(LEGEND);
    }

    /**
     * Una empresa sin Veri*Factu activado emite la misma factura sin QR y sin leyenda. Imprimirla
     * igualmente sería afirmar ante el cliente algo que no ha ocurrido.
     */
    @Test
    void anInvoiceWithoutARecordCarriesNoLegend() throws IOException {
        String text = textOf(renderer.render(content(1, null)));

        assertThat(text).doesNotContain("VERI*FACTU").doesNotContain("verificable");
    }

    @Test
    void aShortInvoiceFitsInOnePage() throws IOException {
        assertThat(pagesOf(renderer.render(content(12, verifactu())))).isEqualTo(1);
    }

    /**
     * Una factura larga continúa en más hojas en vez de perder líneas. Es el fallo silencioso más
     * caro que puede tener una impresión: se entrega una factura incompleta y cuadra igual.
     */
    @Test
    void aLongInvoiceContinuesOnFurtherPages() throws IOException {
        byte[] pdf = renderer.render(content(80, verifactu()));

        assertThat(pagesOf(pdf)).isGreaterThan(1);
        assertThat(textOf(pdf)).contains("Continúa en la hoja siguiente").contains("Hoja 1 de");
    }

    /**
     * Las fuentes estándar del PDF no cubren todo Unicode. Un emoji pegado en la descripción de una
     * línea no puede impedir emitir la factura.
     */
    @Test
    void charactersOutsideTheFontEncodingDoNotBreakTheInvoice() {
        InvoicePdfContent base = content(1, verifactu());
        List<InvoicePdfContent.Line> odd = List.of(new InvoicePdfContent.Line(1, "P1",
                "Vidrio 😀 laminado", new BigDecimal("1.00"), new BigDecimal("10.00"),
                BigDecimal.ZERO, new BigDecimal("21.00"), new BigDecimal("10.00")));
        InvoicePdfContent content = new InvoicePdfContent(base.issuer(), base.recipient(), base.title(),
                base.number(), base.issueDate(), base.dueDate(), base.invoiceKind(), null, null, "EUR",
                odd, base.taxes(), base.netAmount(), base.taxAmount(), base.totalAmount(),
                base.paymentMethod(), base.notes(), base.verifactu(), base.logo());

        assertThat(renderer.render(content)).isNotEmpty();
    }

    /**
     * Un texto que no cabe se recorta con puntos suspensivos, no desaparece.
     *
     * <p>Es la regla que faltaba: la leyenda de Veri*Factu se estaba imprimiendo a medias y nadie
     * lo habría notado, porque lo que se pierde no deja hueco. Si algo no cabe, tiene que verse
     * que no cabe.</p>
     */
    @Test
    void textThatDoesNotFitIsMarkedAsCutInsteadOfVanishing() throws IOException {
        InvoicePdfContent base = content(1, verifactu());
        String longNote = "Condiciones de la operación repetidas hasta no caber en el espacio "
                + "reservado a las observaciones de la factura. ".repeat(6);
        InvoicePdfContent content = new InvoicePdfContent(base.issuer(), base.recipient(), base.title(),
                base.number(), base.issueDate(), base.dueDate(), base.invoiceKind(), null, null, "EUR",
                base.lines(), base.taxes(), base.netAmount(), base.taxAmount(), base.totalAmount(),
                base.paymentMethod(), longNote, base.verifactu(), base.logo());

        assertThat(textOf(renderer.render(content))).contains("…");
    }
}
