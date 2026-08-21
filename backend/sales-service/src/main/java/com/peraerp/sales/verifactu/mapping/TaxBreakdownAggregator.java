package com.peraerp.sales.verifactu.mapping;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.document.DocumentLine;
import com.peraerp.sales.verifactu.domain.ExemptionCause;
import com.peraerp.sales.verifactu.domain.OperationQualification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa las líneas de una factura en el desglose que espera la AEAT.
 *
 * <p>El registro de facturación no lleva las líneas: lleva un resumen por combinación fiscal. Dos
 * líneas al 21 % en régimen general son una sola entrada del desglose; una al 21 % y otra exenta
 * por exportación son dos, aunque el importe sea el mismo.</p>
 *
 * <p>La AEAT admite un máximo de <strong>12</strong> entradas. No es una limitación caprichosa:
 * una factura con más de doce situaciones fiscales distintas casi siempre es un error de captura,
 * y es mejor detenerla que remitir un registro que será rechazado.</p>
 */
@Component
public class TaxBreakdownAggregator {

    /** Máximo de entradas del desglose admitido por la AEAT. */
    public static final int MAX_ENTRIES = 12;

    private final String defaultRegimeKey;
    private final OperationQualification defaultQualification;

    public TaxBreakdownAggregator() {
        this("01", OperationQualification.SUBJECT_NOT_EXEMPT);
    }

    TaxBreakdownAggregator(String defaultRegimeKey, OperationQualification defaultQualification) {
        this.defaultRegimeKey = defaultRegimeKey;
        this.defaultQualification = defaultQualification;
    }

    /**
     * Construye el desglose de una factura.
     *
     * @param lines            líneas de la factura, con importes ya en euros
     * @param fallbackRegime   clave de régimen de la empresa, para las líneas sin código fiscal
     * @param fallbackQualification calificación de la empresa, para las líneas sin código fiscal
     */
    public List<TaxBreakdownEntry> aggregate(List<DocumentLine> lines, String fallbackRegime,
                                             OperationQualification fallbackQualification) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("Una factura sin líneas no puede generar desglose.");
        }
        String regimeDefault = blankToNull(fallbackRegime) == null ? defaultRegimeKey : fallbackRegime.trim();
        OperationQualification qualificationDefault =
                fallbackQualification == null ? defaultQualification : fallbackQualification;

        // LinkedHashMap para que el desglose salga siempre en el mismo orden ante las mismas
        // líneas: un registro tiene que ser reproducible, y el orden forma parte del XML.
        Map<BreakdownKey, Accumulator> grouped = new LinkedHashMap<>();
        for (DocumentLine line : lines) {
            BreakdownKey key = keyFor(line, regimeDefault, qualificationDefault);
            grouped.computeIfAbsent(key, ignored -> new Accumulator()).add(line);
        }

        if (grouped.size() > MAX_ENTRIES) {
            throw new BusinessRuleException("El desglose tiene " + grouped.size()
                    + " combinaciones fiscales distintas y la AEAT admite " + MAX_ENTRIES
                    + " como máximo. Revisa los impuestos de las líneas o divide la factura.");
        }

        List<TaxBreakdownEntry> breakdown = new ArrayList<>(grouped.size());
        grouped.forEach((key, accumulator) -> breakdown.add(new TaxBreakdownEntry(
                key.regimeKey(), key.qualification(), key.exemptionCause(), key.taxRate(),
                accumulator.taxableBase(), accumulator.taxAmount())));
        return List.copyOf(breakdown);
    }

    private BreakdownKey keyFor(DocumentLine line, String regimeDefault,
                                OperationQualification qualificationDefault) {
        OperationQualification qualification = line.getTaxQualificationSnapshot() == null
                ? qualificationDefault : line.getTaxQualificationSnapshot();
        ExemptionCause cause = qualification.isExempt() ? exemptionCauseOf(line) : null;
        String regime = blankToNull(line.getTaxRegimeKeySnapshot()) == null
                ? regimeDefault : line.getTaxRegimeKeySnapshot().trim();
        // El tipo se normaliza a dos decimales: 21 y 21.00 son el mismo tipo impositivo y no
        // pueden acabar en entradas distintas del desglose.
        BigDecimal rate = zeroIfNull(line.getTaxPercentage()).setScale(2, RoundingMode.HALF_UP);
        return new BreakdownKey(regime, qualification, cause, rate);
    }

    /**
     * Una línea exenta sin causa registrada es un dato incompleto, no un caso a resolver por
     * defecto: cambiar la causa cambia lo que se declara.
     */
    private ExemptionCause exemptionCauseOf(DocumentLine line) {
        if (line.getTaxExemptionCauseSnapshot() == null) {
            throw new BusinessRuleException("La línea «" + line.getDescription()
                    + "» está exenta pero no indica la causa de exención. Revisa su código fiscal.");
        }
        return line.getTaxExemptionCauseSnapshot();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Combinación fiscal que define una entrada del desglose. */
    private record BreakdownKey(String regimeKey, OperationQualification qualification,
                                ExemptionCause exemptionCause, BigDecimal taxRate) {
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BreakdownKey key)) return false;
            return regimeKey.equals(key.regimeKey) && qualification == key.qualification
                    && exemptionCause == key.exemptionCause && taxRate.compareTo(key.taxRate) == 0;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(regimeKey, qualification, exemptionCause, taxRate.stripTrailingZeros());
        }
    }

    private static final class Accumulator {
        private BigDecimal taxableBase = BigDecimal.ZERO;
        private BigDecimal taxAmount = BigDecimal.ZERO;

        void add(DocumentLine line) {
            taxableBase = taxableBase.add(zeroIfNull(line.getNetAmount()));
            taxAmount = taxAmount.add(zeroIfNull(line.getTaxAmount()));
        }

        /**
         * Se redondea al sumar, no antes. Redondear cada línea y luego sumar produce desviaciones
         * de céntimos frente al total de la factura, y el total sí viaja en el registro.
         */
        BigDecimal taxableBase() { return taxableBase.setScale(2, RoundingMode.HALF_UP); }

        BigDecimal taxAmount() { return taxAmount.setScale(2, RoundingMode.HALF_UP); }
    }

    /** Orden estable para comparar dos desgloses en pruebas o diagnósticos. */
    public static Comparator<TaxBreakdownEntry> stableOrder() {
        return Comparator.comparing(TaxBreakdownEntry::regimeKey)
                .thenComparing(entry -> entry.qualification().name())
                .thenComparing(entry -> entry.exemptionCause() == null ? "" : entry.exemptionCause().name())
                .thenComparing(TaxBreakdownEntry::taxRate);
    }
}
