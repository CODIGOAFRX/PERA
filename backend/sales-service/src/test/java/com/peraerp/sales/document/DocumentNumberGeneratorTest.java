package com.peraerp.sales.document;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.numbering.NumberingCounter;
import com.peraerp.sales.numbering.NumberingCounterRepository;
import com.peraerp.sales.numbering.NumberingPatternFormatter;
import com.peraerp.sales.numbering.NumberingResetPeriod;
import com.peraerp.sales.numbering.NumberingScheme;
import com.peraerp.sales.numbering.NumberingSchemeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentNumberGeneratorTest {

    @Test
    void incrementsConfiguredCounterAndFormatsBusinessNumber() {
        UUID companyId = UUID.randomUUID();
        UUID schemeId = UUID.randomUUID();
        NumberingScheme scheme = scheme(companyId, schemeId, DocumentType.INVOICE, true);
        NumberingCounter counter = new NumberingCounter(companyId, schemeId, "202608", 41);
        NumberingSchemeRepository schemes = mock(NumberingSchemeRepository.class);
        NumberingCounterRepository counters = mock(NumberingCounterRepository.class);
        when(schemes.findByCompanyIdAndDocumentTypeAndDefaultSchemeTrueAndActiveTrue(companyId, DocumentType.INVOICE))
                .thenReturn(Optional.of(scheme));
        when(counters.findByCompanyIdAndSchemeIdAndPeriodKey(companyId, schemeId, "202608"))
                .thenReturn(Optional.of(counter));
        when(counters.save(any(NumberingCounter.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentNumberGenerator generator = new DocumentNumberGenerator(schemes, counters,
                new NumberingPatternFormatter());

        String first = generator.next(companyId, DocumentType.INVOICE, LocalDate.of(2026, 8, 10), null);
        String second = generator.next(companyId, DocumentType.INVOICE, LocalDate.of(2026, 8, 10), null);

        assertThat(first).isEqualTo("FAC-20260810-000041");
        assertThat(second).isEqualTo("FAC-20260810-000042");
        verify(counters, times(2)).ensureCounter(any(UUID.class), eq(companyId), eq(schemeId), eq("202608"),
                eq(41L), any());
    }

    @Test
    void rejectsRequestedSchemeFromAnotherCompany() {
        UUID companyId = UUID.randomUUID();
        UUID schemeId = UUID.randomUUID();
        NumberingSchemeRepository schemes = mock(NumberingSchemeRepository.class);
        when(schemes.findByIdAndCompanyId(schemeId, companyId)).thenReturn(Optional.empty());
        DocumentNumberGenerator generator = new DocumentNumberGenerator(schemes,
                mock(NumberingCounterRepository.class), new NumberingPatternFormatter());

        assertThatThrownBy(() -> generator.next(companyId, DocumentType.QUOTE,
                LocalDate.of(2026, 8, 10), schemeId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empresa activa");
    }

    private NumberingScheme scheme(UUID companyId, UUID id, DocumentType type, boolean active) {
        NumberingScheme scheme = new NumberingScheme(companyId, "FAC", "Facturas", type, "FAC",
                "{series}-{yyyy}{MM}{dd}-{seq:6}", NumberingResetPeriod.MONTHLY, 41, active, true);
        ReflectionTestUtils.setField(scheme, "id", id);
        return scheme;
    }
}
