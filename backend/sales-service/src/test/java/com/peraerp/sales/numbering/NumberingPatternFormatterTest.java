package com.peraerp.sales.numbering;

import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumberingPatternFormatterTest {

    private final NumberingPatternFormatter formatter = new NumberingPatternFormatter();

    @Test
    void formatsAllSupportedDateAndSequenceTokens() {
        assertThat(formatter.format("{series}/{yy}/{MM}/{dd}/{seq:4}", "PED",
                LocalDate.of(2026, 8, 10), 27))
                .isEqualTo("PED/26/08/10/0027");
    }

    @Test
    void rejectsUnknownTokensAndMissingSequence() {
        assertThatThrownBy(() -> formatter.validate("FAC-{customer}-{seq:6}"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no permitido");
        assertThatThrownBy(() -> formatter.validate("FAC-{yyyy}"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("{seq}");
    }

    @Test
    void rejectsUnsafeSequenceWidths() {
        assertThatThrownBy(() -> formatter.validate("FAC-{seq:99}"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("entre 1 y 12");
    }
}
