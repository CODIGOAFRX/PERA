package com.peraerp.sales.print;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lo mínimo para dibujar un documento con recuadros: texto colocado, cajas y reglas.
 *
 * <p>PDFBox trabaja en puntos y con el origen abajo a la izquierda, que es incómodo para maquetar
 * de arriba abajo. Esta clase no lo esconde —las coordenadas siguen siendo las del PDF— pero quita
 * la ceremonia repetida de abrir y cerrar bloques de texto y de medir cadenas.</p>
 *
 * <p>Envuelve {@link IOException} en {@link UncheckedIOException} a propósito: escribir en un
 * búfer de memoria no falla por causas que el maquetador pueda tratar, y propagar la excepción
 * comprobada por cada línea de texto llenaría el renderizador de ruido.</p>
 */
class PdfCanvas {

    /**
     * Las fuentes estándar del PDF codifican en WinAnsi, que cubre el español entero pero no todo
     * Unicode. Un carácter fuera de ese juego —un emoji pegado en la descripción de una línea—
     * haría fallar la generación entera. Se sustituye, porque perder un signo raro es mejor que no
     * poder emitir la factura.
     */
    private static final String WIN_ANSI_EXTRA = "€‚ƒ„…†‡ˆ‰"
            + "Š‹ŒŽ‘’“”•–—˜™š"
            + "›œžŸ";

    private final PDPageContentStream stream;

    PdfCanvas(PDPageContentStream stream) {
        this.stream = stream;
    }

    void text(PDFont font, float size, float x, float y, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(printable(value));
            stream.endText();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir texto en el PDF.", e);
        }
    }

    /** Texto pegado al borde derecho de {@code right}, para las columnas de importes. */
    void textRight(PDFont font, float size, float right, float y, String value) {
        text(font, size, right - width(font, size, value), y, value);
    }

    void textCentred(PDFont font, float size, float centre, float y, String value) {
        text(font, size, centre - width(font, size, value) / 2, y, value);
    }

    /**
     * Texto recortado para que quepa en un ancho.
     *
     * <p>Recorta en vez de partir en varias líneas porque las filas de la factura tienen altura
     * fija: una descripción larga que se partiera desplazaría toda la rejilla hacia abajo.</p>
     */
    void textClipped(PDFont font, float size, float x, float y, float maxWidth, String value) {
        text(font, size, x, y, clip(font, size, value, maxWidth));
    }

    String clip(PDFont font, float size, String value, float maxWidth) {
        if (value == null || width(font, size, value) <= maxWidth) {
            return value;
        }
        String ellipsis = "…";
        float room = maxWidth - width(font, size, ellipsis);
        StringBuilder kept = new StringBuilder();
        for (char character : printable(value).toCharArray()) {
            if (width(font, size, kept.toString() + character) > room) {
                break;
            }
            kept.append(character);
        }
        return kept + ellipsis;
    }

    /**
     * Parte un texto en líneas respetando las palabras.
     *
     * <p>Si no cabe en {@code maxLines}, lo que sobra se junta en la última línea y se recorta con
     * puntos suspensivos. Nunca desaparece en silencio: un texto que se corta sin avisar es un
     * texto que nadie echa en falta, y aquí se imprimen leyendas legales.</p>
     */
    List<String> wrap(PDFont font, float size, String value, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : printable(value).trim().split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (current.isEmpty() || width(font, size, candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        if (lines.size() <= maxLines) {
            return lines;
        }
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines - 1));
        kept.add(clip(font, size, String.join(" ", lines.subList(maxLines - 1, lines.size())), maxWidth));
        return kept;
    }

    float width(PDFont font, float size, String value) {
        if (value == null || value.isEmpty()) {
            return 0f;
        }
        try {
            return font.getStringWidth(printable(value)) / 1000 * size;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo medir el texto del PDF.", e);
        }
    }

    void line(float fromX, float fromY, float toX, float toY, float thickness, float grey) {
        try {
            stream.setLineWidth(thickness);
            stream.setStrokingColor(grey, grey, grey);
            stream.moveTo(fromX, fromY);
            stream.lineTo(toX, toY);
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo dibujar una línea del PDF.", e);
        }
    }

    void box(float x, float bottom, float width, float height, float thickness, float grey) {
        try {
            stream.setLineWidth(thickness);
            stream.setStrokingColor(grey, grey, grey);
            stream.addRect(x, bottom, width, height);
            stream.stroke();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo dibujar un recuadro del PDF.", e);
        }
    }

    void fill(float x, float bottom, float width, float height, float grey) {
        try {
            stream.setNonStrokingColor(grey, grey, grey);
            stream.addRect(x, bottom, width, height);
            stream.fill();
            stream.setNonStrokingColor(0f, 0f, 0f);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo rellenar un recuadro del PDF.", e);
        }
    }

    /** Caja rellena y con borde, que es como se dibujan las cabeceras de las tablas. */
    void filledBox(float x, float bottom, float width, float height, float fillGrey, float lineGrey) {
        fill(x, bottom, width, height, fillGrey);
        box(x, bottom, width, height, 0.6f, lineGrey);
    }

    private static String printable(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            boolean supported = (character >= 32 && character <= 126)
                    || (character >= 160 && character <= 255)
                    || WIN_ANSI_EXTRA.indexOf(character) >= 0;
            safe.append(supported ? character : '?');
        }
        return safe.toString();
    }
}
