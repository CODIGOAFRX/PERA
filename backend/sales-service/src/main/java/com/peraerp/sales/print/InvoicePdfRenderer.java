package com.peraerp.sales.print;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dibuja la factura en A4.
 *
 * <p>Se genera aquí y no en el navegador porque la factura es un entregable: hay que poder
 * enviarla por correo, archivarla y reimprimirla igual dentro de cinco años. Una impresión de
 * navegador depende de qué navegador, con qué márgenes y con qué ajustes del diálogo, y no produce
 * ningún fichero.</p>
 *
 * <p>La maqueta es de recuadros y rejilla, no de página web: es lo que espera quien recibe una
 * factura y lo que hacen los programas del sector. Las medidas están en puntos, con el origen
 * abajo a la izquierda, que es como trabaja el PDF.</p>
 */
@Component
public class InvoicePdfRenderer {

    private static final Locale SPAIN = Locale.forLanguageTag("es-ES");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final float LEFT = 32f;
    private static final float RIGHT = 563f;
    private static final float TOP = 810f;
    private static final float BOTTOM = 40f;

    /** 35 mm de lado: la especificación del QR tributario lo fija entre 30 y 40 mm impreso. */
    private static final float QR_SIDE = 99.2f;

    /** Leyenda del art. 26 del RD 1007/2023, junto al QR. Va literal y completa. */
    private static final String VERIFACTU_LEGEND =
            "Factura verificable en la sede electrónica de la AEAT";

    private static final float ROW_HEIGHT = 13f;
    private static final float TOTALS_ROW_HEIGHT = 15f;
    private static final float TOTALS_BOTTOM = 130f;
    private static final float PAYMENT_BOTTOM = 96f;
    private static final float FOOTER_TOP = 90f;

    private static final float LINE_GREY = 0.35f;
    private static final float RULE_GREY = 0.15f;
    private static final float HEADER_FILL = 0.86f;
    private static final float SOFT_FILL = 0.95f;

    /** N.º, descripción, cantidad, precio, descuento, IVA e importe: suman los 531 pt útiles. */
    private static final float[] COLUMNS = {26f, 231f, 55f, 62f, 42f, 42f, 73f};

    /**
     * Las fuentes se crean por documento y no se guardan en el componente.
     *
     * <p>Un {@link PDFont} lleva dentro el objeto COS que acaba en los recursos de la página.
     * Compartir la misma instancia entre dos documentos que se generan a la vez —y este componente
     * es un singleton que atiende peticiones concurrentes— mezcla los recursos de uno con los del
     * otro. Crearlas cada vez no cuesta nada: son las catorce fuentes estándar del PDF.</p>
     */
    private PDFont regular;
    private PDFont bold;
    private PDFont italic;

    public byte[] render(InvoicePdfContent content) {
        return new InvoicePdfRenderer().draw(content);
    }

    private byte[] draw(InvoicePdfContent content) {
        regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            float tableTop = 574f;
            float tableBottom = totalsTop(content) + 10f;
            int rowsPerPage = Math.max(1, (int) ((tableTop - tableBottom) / ROW_HEIGHT));
            List<List<InvoicePdfContent.Line>> pages = paginate(content.lines(), rowsPerPage);

            for (int index = 0; index < pages.size(); index++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                boolean last = index == pages.size() - 1;
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    PdfCanvas canvas = new PdfCanvas(stream);
                    drawHeader(document, stream, canvas, content, index == 0);
                    drawInvoiceIdentity(canvas, content);
                    drawLines(canvas, content, pages.get(index), tableTop, tableBottom, last);
                    if (last) {
                        drawTotals(canvas, content);
                        drawPayment(canvas, content);
                    }
                    drawFooter(canvas, content, index + 1, pages.size());
                }
            }
            document.save(output);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF de la factura.", e);
        }
        return output.toByteArray();
    }

    // --- cabecera ---

    private void drawHeader(PDDocument document, PDPageContentStream stream, PdfCanvas canvas,
                            InvoicePdfContent content, boolean first) throws IOException {
        InvoicePdfContent.Issuer issuer = content.issuer();
        float cursor = TOP;

        if (content.logo() != null && content.logo().length > 0) {
            cursor = drawLogo(document, stream, content.logo(), cursor);
        }
        cursor -= 12f;
        canvas.text(bold, 12.5f, LEFT, cursor, issuer.legalName());
        cursor -= 13f;
        canvas.text(regular, 9f, LEFT, cursor, "NIF " + nullToDash(issuer.taxId()));

        cursor -= 13f;
        for (String line : issuerAddress(issuer)) {
            canvas.text(regular, 8f, LEFT, cursor, line);
            cursor -= 9.5f;
        }

        // El QR va en la parte superior y solo en la primera hoja, que es donde lo pide la
        // especificación. Repetirlo en cada hoja invita a cotejar una página suelta como si fuera
        // la factura.
        if (first && content.verifactu() != null) {
            drawVerificationCode(document, stream, canvas, content.verifactu());
        }
        canvas.line(LEFT, 676f, RIGHT, 676f, 1.2f, RULE_GREY);
    }

    /** El logotipo se encaja en su hueco sin deformarlo: una factura con el logo estirado canta. */
    private float drawLogo(PDDocument document, PDPageContentStream stream, byte[] logo, float top)
            throws IOException {
        float maxWidth = 150f;
        float maxHeight = 38f;
        PDImageXObject image;
        try {
            image = PDImageXObject.createFromByteArray(document, logo, "logo");
        } catch (IOException | IllegalArgumentException unsupported) {
            // Los ajustes admiten WebP, que el PDF no sabe incrustar. Mejor una factura sin logo
            // que una factura que no se puede emitir.
            return top;
        }
        float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        stream.drawImage(image, LEFT, top - height, width, height);
        return top - height - 4f;
    }

    /**
     * QR de cotejo y leyenda, arriba a la derecha y solo en la primera página, que es donde la
     * especificación pide que estén.
     */
    private void drawVerificationCode(PDDocument document, PDPageContentStream stream, PdfCanvas canvas,
                                      InvoicePdfContent.Verifactu verifactu) throws IOException {
        float left = RIGHT - QR_SIDE;
        float bottom = TOP - QR_SIDE;
        PDImageXObject image = LosslessFactory.createFromImage(document, QrImages.of(verifactu.qrPayload()));
        stream.drawImage(image, left, bottom, QR_SIDE, QR_SIDE);

        // El QR, la leyenda y los importes comparten el borde derecho de la caja. La leyenda es
        // más ancha que el QR y puede crecer hacia la izquierda, sobre el blanco, pero nunca
        // asomarse fuera del margen: alineada a la derecha no hay forma de que lo haga.
        canvas.textRight(bold, 9.5f, RIGHT, bottom - 11f, "VERI*FACTU");
        float y = bottom - 20f;
        for (String line : canvas.wrap(regular, 6.2f, VERIFACTU_LEGEND, QR_SIDE + 60f, 3)) {
            canvas.textRight(regular, 6.2f, RIGHT, y, line);
            y -= 7.5f;
        }
    }

    // --- identidad de la factura ---

    private void drawInvoiceIdentity(PdfCanvas canvas, InvoicePdfContent content) {
        float titleWidth = 250f;
        canvas.filledBox(LEFT, 638f, titleWidth, 30f, HEADER_FILL, RULE_GREY);
        canvas.text(bold, 15f, LEFT + 12f, 648f, content.title());
        canvas.textRight(bold, 15f, LEFT + titleWidth - 12f, 648f, content.number());

        drawIdentityData(canvas, content, titleWidth);
        drawRecipient(canvas, content.recipient());

        if (content.rectifiedNumber() != null) {
            String rectifies = "Rectifica a la factura " + content.rectifiedNumber()
                    + (content.rectifiedIssueDate() == null ? "" : " de " + DATE.format(content.rectifiedIssueDate()));
            canvas.text(italic, 8f, LEFT, 592f, rectifies);
        }
    }

    /** Fecha, vencimiento y tipo de factura, en tres casillas con su rótulo encima. */
    private void drawIdentityData(PdfCanvas canvas, InvoicePdfContent content, float totalWidth) {
        String[] labels = {"Fecha", "Vencimiento", "Tipo"};
        String[] values = {
                DATE.format(content.issueDate()),
                content.dueDate() == null ? "—" : DATE.format(content.dueDate()),
                nullToDash(content.invoiceKind()),
        };
        float cell = totalWidth / labels.length;
        for (int index = 0; index < labels.length; index++) {
            float x = LEFT + cell * index;
            canvas.filledBox(x, 617f, cell, 13f, SOFT_FILL, LINE_GREY);
            canvas.box(x, 602f, cell, 15f, 0.6f, LINE_GREY);
            canvas.textCentred(bold, 7.5f, x + cell / 2, 620.5f, labels[index]);
            canvas.textCentred(regular, 9f, x + cell / 2, 606f, values[index]);
        }
    }

    private void drawRecipient(PdfCanvas canvas, InvoicePdfContent.Recipient recipient) {
        float left = 312f;
        float width = RIGHT - left;
        canvas.box(left, 602f, width, 66f, 0.8f, RULE_GREY);
        canvas.text(bold, 7f, left + 8f, 658f, "DESTINATARIO");
        canvas.textClipped(bold, 10.5f, left + 8f, 644f, width - 16f, recipient.legalName());
        canvas.text(regular, 8.5f, left + 8f, 632f,
                "NIF " + nullToDash(recipient.taxId())
                        + (recipient.code() == null ? "" : "    Código " + recipient.code()));
        if (recipient.addressNotice() != null) {
            float y = 620f;
            for (String line : canvas.wrap(italic, 7f, recipient.addressNotice(), width - 16f, 2)) {
                canvas.text(italic, 7f, left + 8f, y, line);
                y -= 8f;
            }
        }
    }

    // --- líneas ---

    private void drawLines(PdfCanvas canvas, InvoicePdfContent content, List<InvoicePdfContent.Line> rows,
                           float top, float bottom, boolean last) {
        String[] headings = {"N.º", "Descripción", "Cantidad", "Precio", "Dto. %", "IVA %", "Importe"};
        canvas.filledBox(LEFT, top, RIGHT - LEFT, 16f, HEADER_FILL, RULE_GREY);
        float x = LEFT;
        for (int index = 0; index < COLUMNS.length; index++) {
            if (index >= 2) {
                canvas.textRight(bold, 7.5f, x + COLUMNS[index] - 4f, top + 5f, headings[index]);
            } else {
                canvas.text(bold, 7.5f, x + 4f, top + 5f, headings[index]);
            }
            x += COLUMNS[index];
        }

        // El marco se dibuja entero aunque sobren filas. Una caja que se encoge con el contenido
        // deja la factura con un agujero y da la impresión de que falta algo.
        canvas.box(LEFT, bottom, RIGHT - LEFT, top - bottom, 0.8f, RULE_GREY);
        x = LEFT;
        for (int index = 0; index < COLUMNS.length - 1; index++) {
            x += COLUMNS[index];
            canvas.line(x, bottom, x, top, 0.5f, LINE_GREY);
        }

        float y = top - ROW_HEIGHT;
        for (InvoicePdfContent.Line row : rows) {
            drawLine(canvas, content, row, y);
            y -= ROW_HEIGHT;
        }
        if (!last) {
            canvas.text(italic, 7.5f, LEFT + 6f, bottom + 4f, "Continúa en la hoja siguiente");
        }
    }

    private void drawLine(PdfCanvas canvas, InvoicePdfContent content, InvoicePdfContent.Line row, float y) {
        float baseline = y + 4f;
        float x = LEFT;
        canvas.text(regular, 8f, x + 4f, baseline, String.valueOf(row.order()));
        x += COLUMNS[0];

        String description = row.code() == null || row.code().isBlank()
                ? row.description() : row.description() + "  (" + row.code() + ")";
        canvas.textClipped(regular, 8f, x + 4f, baseline, COLUMNS[1] - 8f, description);
        x += COLUMNS[1];

        String[] figures = {
                quantity(row.quantity()),
                amount(row.unitPrice()),
                percentage(row.discountPercentage()),
                percentage(row.taxPercentage()),
                amount(row.netAmount()),
        };
        for (int index = 0; index < figures.length; index++) {
            float columnWidth = COLUMNS[index + 2];
            canvas.textRight(regular, 8f, x + columnWidth - 4f, baseline, figures[index]);
            x += columnWidth;
        }
    }

    // --- totales ---

    private float totalsTop(InvoicePdfContent content) {
        int rows = content.taxes().size() + 2;
        return TOTALS_BOTTOM + TOTALS_ROW_HEIGHT * rows;
    }

    private void drawTotals(PdfCanvas canvas, InvoicePdfContent content) {
        float top = totalsTop(content);
        float width = RIGHT - LEFT;
        float[] cells = {width * 0.34f, width * 0.22f, width * 0.22f, width * 0.22f};
        String[] headings = {"Desglose", "Base imponible", "Cuota", "Total"};

        float y = top - TOTALS_ROW_HEIGHT;
        canvas.filledBox(LEFT, y, width, TOTALS_ROW_HEIGHT, HEADER_FILL, RULE_GREY);
        float x = LEFT;
        for (int index = 0; index < cells.length; index++) {
            if (index == 0) {
                canvas.text(bold, 7.5f, x + 6f, y + 4.5f, headings[index]);
            } else {
                canvas.textRight(bold, 7.5f, x + cells[index] - 6f, y + 4.5f, headings[index]);
            }
            x += cells[index];
        }

        for (InvoicePdfContent.TaxRow tax : content.taxes()) {
            y -= TOTALS_ROW_HEIGHT;
            canvas.box(LEFT, y, width, TOTALS_ROW_HEIGHT, 0.5f, LINE_GREY);
            String[] figures = {
                    "IVA " + percentage(tax.rate()) + " %",
                    amount(tax.base()),
                    amount(tax.amount()),
                    amount(tax.base().add(tax.amount())),
            };
            x = LEFT;
            for (int index = 0; index < cells.length; index++) {
                if (index == 0) {
                    canvas.text(regular, 8f, x + 6f, y + 4.5f, figures[index]);
                } else {
                    canvas.textRight(regular, 8f, x + cells[index] - 6f, y + 4.5f, figures[index]);
                }
                x += cells[index];
            }
        }

        y -= TOTALS_ROW_HEIGHT;
        canvas.filledBox(LEFT, y, width, TOTALS_ROW_HEIGHT, SOFT_FILL, RULE_GREY);
        canvas.text(bold, 9f, LEFT + 6f, y + 4.5f, "Total factura");
        canvas.textRight(bold, 8f, LEFT + cells[0] + cells[1] - 6f, y + 4.5f, amount(content.netAmount()));
        canvas.textRight(bold, 8f, LEFT + cells[0] + cells[1] + cells[2] - 6f, y + 4.5f, amount(content.taxAmount()));
        canvas.textRight(bold, 10f, RIGHT - 6f, y + 4f, money(content.totalAmount(), content.currency()));
    }

    private void drawPayment(PdfCanvas canvas, InvoicePdfContent content) {
        float y = PAYMENT_BOTTOM;
        canvas.text(bold, 8f, LEFT, y + 12f, "Forma de pago");
        canvas.text(regular, 8f, LEFT + 68f, y + 12f, nullToDash(content.paymentMethod()));
        // El vencimiento ya sale arriba en su casilla. Aquí lo que interesa es a cuánto asciende
        // lo que hay que pagar y cuándo, junto: repetir solo la fecha no aporta nada.
        canvas.text(bold, 8f, LEFT, y + 1f, "Importe a pagar");
        canvas.text(regular, 8f, LEFT + 68f, y + 1f,
                money(content.totalAmount(), content.currency())
                        + (content.dueDate() == null ? "  a la vista" : "  el " + DATE.format(content.dueDate())));
        if (content.notes() != null && !content.notes().isBlank()) {
            float x = 300f;
            canvas.text(bold, 7.5f, x, y + 12f, "Observaciones");
            float noteY = y + 3f;
            for (String line : canvas.wrap(regular, 7f, content.notes(), RIGHT - x, 2)) {
                canvas.text(regular, 7f, x, noteY, line);
                noteY -= 8f;
            }
        }
    }

    // --- pie ---

    private void drawFooter(PdfCanvas canvas, InvoicePdfContent content, int page, int pages) {
        canvas.line(LEFT, FOOTER_TOP, RIGHT, FOOTER_TOP, 0.5f, LINE_GREY);
        canvas.textRight(regular, 7f, RIGHT, FOOTER_TOP - 10f, "Hoja " + page + " de " + pages);

        if (content.verifactu() == null) {
            return;
        }
        // La huella va también en texto, no solo dentro del QR: si el código se borra o se fotocopia
        // mal, el cotejo sigue siendo posible a mano.
        canvas.text(bold, 7f, LEFT, FOOTER_TOP - 10f, "Huella del registro Veri*Factu");
        canvas.text(regular, 6.5f, LEFT, FOOTER_TOP - 19f, content.verifactu().fingerprint());
        canvas.text(regular, 6.5f, LEFT, FOOTER_TOP - 28f,
                "Cotejo en " + content.verifactu().validationUrl());
    }

    // --- formato ---

    private static List<List<InvoicePdfContent.Line>> paginate(List<InvoicePdfContent.Line> lines, int perPage) {
        List<List<InvoicePdfContent.Line>> pages = new ArrayList<>();
        for (int index = 0; index < lines.size(); index += perPage) {
            pages.add(lines.subList(index, Math.min(index + perPage, lines.size())));
        }
        if (pages.isEmpty()) {
            pages.add(List.of());
        }
        return pages;
    }

    private static String amount(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(SPAIN);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String money(BigDecimal value, String currency) {
        return amount(value) + " " + ("EUR".equals(currency) ? "€" : currency);
    }

    /**
     * Las cantidades se guardan con seis decimales. Redondear a dos en el papel cambiaría lo que
     * dice la factura, así que se muestran los que hagan falta y nunca menos de dos.
     */
    private static String quantity(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(SPAIN);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(6);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String percentage(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(SPAIN);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    /**
     * Domicilio y contacto del emisor.
     *
     * <p>El art. 6.1.e) del RD 1619/2012 exige el domicilio de las dos partes, no solo el del
     * destinatario. Si la empresa no lo ha rellenado en Ajustes, la factura lo dice en vez de
     * dejar el hueco en blanco, que es lo que hace que nadie lo eche en falta.</p>
     */
    private static List<String> issuerAddress(InvoicePdfContent.Issuer issuer) {
        List<String> lines = new ArrayList<>();
        addIfPresent(lines, issuer.addressLine1());
        addIfPresent(lines, issuer.addressLine2());
        addIfPresent(lines, join(issuer.postalCode(), issuer.city(), issuer.region()));
        if (lines.isEmpty()) {
            lines.add("Domicilio sin configurar: complétalo en Ajustes › Empresa.");
        }
        addIfPresent(lines, issuer.phone() == null ? null : "Teléfono " + issuer.phone());
        addIfPresent(lines, issuer.email());
        addIfPresent(lines, issuer.website());
        return lines;
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value.trim());
        }
    }

    private static String join(String... parts) {
        StringBuilder joined = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                joined.append(joined.isEmpty() ? "" : " · ").append(part.trim());
            }
        }
        return joined.toString();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
