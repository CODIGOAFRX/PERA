package com.peraerp.sales.numbering;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import com.peraerp.sales.config.CurrentCompanyProvider;
import com.peraerp.sales.document.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NumberingSchemeServiceTest {

    private NumberingSchemeRepository repository;
    private NumberingSchemeService service;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        repository = mock(NumberingSchemeRepository.class);
        CurrentCompanyProvider companyProvider = mock(CurrentCompanyProvider.class);
        companyId = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        service = new NumberingSchemeService(repository, new NumberingPatternFormatter(), companyProvider);
    }

    @Test
    void createsNormalizedDefaultSchemeAndClearsPreviousDefault() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "INV-2026")).thenReturn(false);
        when(repository.save(any(NumberingScheme.class))).thenAnswer(invocation -> {
            NumberingScheme scheme = invocation.getArgument(0);
            ReflectionTestUtils.setField(scheme, "id", UUID.randomUUID());
            return scheme;
        });

        NumberingSchemeResponse response = service.create(request(" inv-2026 ", true, true));

        assertThat(response.code()).isEqualTo("INV-2026");
        assertThat(response.series()).isEqualTo("FAC");
        assertThat(response.defaultScheme()).isTrue();
        verify(repository).clearDefault(any(UUID.class), any(DocumentType.class), any(UUID.class));
    }

    @Test
    void rejectsDuplicateCodeAndInactiveDefault() {
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "INV-2026")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("INV-2026", true, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Ya existe");

        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "INV-2026")).thenReturn(false);
        assertThatThrownBy(() -> service.create(request("INV-2026", true, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("debe estar activa");
    }

    @Test
    void lookupCannotCrossTenantBoundary() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void previewsWithoutConsumingCounter() {
        UUID id = UUID.randomUUID();
        NumberingScheme scheme = new NumberingScheme(companyId, "FAC", "Facturas", DocumentType.INVOICE,
                "FAC", "{series}-{yyyy}-{seq:6}", NumberingResetPeriod.YEARLY, 1, true, true);
        ReflectionTestUtils.setField(scheme, "id", id);
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(scheme));

        NumberingPreviewResponse preview = service.preview(id, LocalDate.of(2026, 8, 10), 9L);

        assertThat(preview.value()).isEqualTo("FAC-2026-000009");
    }

    private NumberingSchemeRequest request(String code, boolean defaultScheme, boolean active) {
        return new NumberingSchemeRequest(code, "Facturas", DocumentType.INVOICE, " fac ",
                "{series}-{yyyy}-{seq:6}", NumberingResetPeriod.YEARLY, 1, active, defaultScheme);
    }
}
