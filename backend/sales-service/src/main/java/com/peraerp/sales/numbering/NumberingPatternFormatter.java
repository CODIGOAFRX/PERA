package com.peraerp.sales.numbering;

import com.peraerp.platform.domain.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class NumberingPatternFormatter {

    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]+)}");
    private static final Pattern SEQUENCE = Pattern.compile("\\{seq(?::(\\d{1,2}))?}");
    private static final Set<String> SIMPLE_TOKENS = Set.of("yyyy", "yy", "MM", "dd", "series");

    public void validate(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new BusinessRuleException("El patrón de numeración es obligatorio.");
        }
        if (pattern.length() > 120) {
            throw new BusinessRuleException("El patrón de numeración no puede superar 120 caracteres.");
        }
        Matcher matcher = TOKEN.matcher(pattern);
        boolean sequenceFound = false;
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token.equals("seq") || token.matches("seq:\\d{1,2}")) {
                int width = token.equals("seq") ? 6 : Integer.parseInt(token.substring(4));
                if (width < 1 || width > 12) {
                    throw new BusinessRuleException("El ancho de la secuencia debe estar entre 1 y 12.");
                }
                sequenceFound = true;
            } else if (!SIMPLE_TOKENS.contains(token)) {
                throw new BusinessRuleException("El patrón contiene el token no permitido {" + token + "}.");
            }
        }
        if (!sequenceFound) {
            throw new BusinessRuleException("El patrón debe incluir {seq} o {seq:N}.");
        }
    }

    public String format(String pattern, String series, LocalDate date, long sequence) {
        validate(pattern);
        String result = pattern
                .replace("{yyyy}", "%04d".formatted(date.getYear()))
                .replace("{yy}", "%02d".formatted(date.getYear() % 100))
                .replace("{MM}", "%02d".formatted(date.getMonthValue()))
                .replace("{dd}", "%02d".formatted(date.getDayOfMonth()))
                .replace("{series}", series);
        Matcher matcher = SEQUENCE.matcher(result);
        StringBuffer formatted = new StringBuffer();
        while (matcher.find()) {
            int width = matcher.group(1) == null ? 6 : Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(formatted, Matcher.quoteReplacement(("%0" + width + "d").formatted(sequence)));
        }
        matcher.appendTail(formatted);
        if (formatted.length() > 80) {
            throw new BusinessRuleException("El número generado supera los 80 caracteres.");
        }
        return formatted.toString();
    }
}
