package com.peraerp.sales.integration;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fichero {@code factura_e.ini} de una empresa, con el mismo formato que el de DimproCristalWin.
 *
 * <p>Secciones entre corchetes y líneas {@code clave = valor}. Las líneas que empiezan por {@code ;},
 * {@code #} o {@code *} son comentarios (Dimpro desactiva claves poniéndoles un asterisco delante).</p>
 *
 * <p>Dimpro lee {@code [VERIFACTI]} y {@code [B2BROUTER]}, pero circulan ficheros con {@code [VERI_FACTI]}
 * y {@code [B2B_ROUTER]} que allí se leen vacíos sin avisar. Aquí los nombres de sección y de clave no
 * distinguen mayúsculas ni guiones bajos, para que ese error no deje a un cliente sin facturar.</p>
 */
final class FacturaEIni {
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private final Map<String, Map<String, String>> sections;

    private FacturaEIni(Map<String, Map<String, String>> sections) {
        this.sections = sections;
    }

    /** Dimpro guarda sus .ini en ANSI; si el fichero es UTF-8 válido (con o sin BOM) se lee como UTF-8. */
    static FacturaEIni parse(byte[] content) {
        return parse(decode(content));
    }

    static FacturaEIni parse(String text) {
        Map<String, Map<String, String>> sections = new HashMap<>();
        Map<String, String> current = sections.computeIfAbsent("", ignored -> new HashMap<>());
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#") || line.startsWith("*")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = sections.computeIfAbsent(normalize(line.substring(1, line.length() - 1)), ignored -> new HashMap<>());
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            current.put(normalize(line.substring(0, equals)), unquote(line.substring(equals + 1).strip()));
        }
        return new FacturaEIni(sections);
    }

    /** Valor de la clave, o vacío si no está. */
    String get(String section, String key) {
        return sections.getOrDefault(normalize(section), Map.of()).getOrDefault(normalize(key), "");
    }

    boolean hasSection(String section) {
        return sections.containsKey(normalize(section));
    }

    private static String normalize(String name) {
        return name.strip().replace("_", "").toLowerCase(Locale.ROOT);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).strip();
        }
        return value;
    }

    private static String decode(byte[] content) {
        int start = content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer bytes = ByteBuffer.wrap(content, start, content.length - start);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(bytes).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(content, start, content.length - start, WINDOWS_1252);
        }
    }
}
