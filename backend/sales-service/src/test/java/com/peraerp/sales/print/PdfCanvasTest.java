package com.peraerp.sales.print;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import static org.assertj.core.api.Assertions.assertThat;

class PdfCanvasTest {
    @Test
    void preservesSpanishAccentsIncludingPastedCombiningCharacters() throws Exception {
        String expected = "á é í ó ú Á É Í Ó Ú ñ Ñ ü Ü ¿Qué? ¡Sí! 847,00 €";
        byte[] bytes;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                PdfCanvas canvas = new PdfCanvas(stream);
                canvas.text(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12, 30, 700,
                        Normalizer.normalize(expected, Normalizer.Form.NFD));
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            bytes = output.toByteArray();
        }
        try (PDDocument parsed = Loader.loadPDF(bytes)) {
            assertThat(new PDFTextStripper().getText(parsed)).contains(expected);
        }
    }
}
