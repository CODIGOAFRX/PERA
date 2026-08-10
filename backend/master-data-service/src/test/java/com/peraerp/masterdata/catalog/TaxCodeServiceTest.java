package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxCodeServiceTest {
    @Mock TaxCodeRepository repository;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private TaxCodeService service;

    @BeforeEach
    void setUp() {
        service = new TaxCodeService(repository, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsNormalizedTaxCode() {
        when(repository.existsByCompanyIdAndCountryCodeAndCodeIgnoreCase(companyId, "ES", "IVA21"))
                .thenReturn(false);
        when(repository.save(any(TaxCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaxCodeResponse response = service.create(request(" es ", " iva21 ", new BigDecimal("21"),
                LocalDate.of(2026, 1, 1), null, false, true));

        assertThat(response.countryCode()).isEqualTo("ES");
        assertThat(response.code()).isEqualTo("IVA21");
        assertThat(response.name()).isEqualTo("Impuesto general");
        assertThat(response.percentage()).isEqualByComparingTo("21");
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsInvalidCountryAndPercentage() {
        assertThatThrownBy(() -> service.create(request("ESP", "IVA", new BigDecimal("21"),
                LocalDate.of(2026, 1, 1), null, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ISO-3166");

        assertThatThrownBy(() -> service.create(request("ES", "IVA", new BigDecimal("101"),
                LocalDate.of(2026, 1, 1), null, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("entre 0 y 100");

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidValidityWindow() {
        assertThatThrownBy(() -> service.create(request("ES", "IVA21", new BigDecimal("21"),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31), false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("anterior");
    }

    @Test
    void requiresZeroPercentageForExemptCode() {
        assertThatThrownBy(() -> service.create(request("ES", "EXENTO", new BigDecimal("21"),
                LocalDate.of(2026, 1, 1), null, true, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("porcentaje cero");
    }

    @Test
    void keepsFiscalCodeImmutable() {
        UUID id = UUID.randomUUID();
        TaxCode taxCode = taxCode("ES", "IVA21", new BigDecimal("21"), true,
                LocalDate.of(2026, 1, 1), null);
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(taxCode));

        assertThatThrownBy(() -> service.update(id, request("ES", "IVA10", new BigDecimal("10"),
                LocalDate.of(2026, 1, 1), null, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");
    }

    @Test
    void hidesTaxCodeFromAnotherCompany() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchesWithTenantAndNormalizedFilters() {
        LocalDate validOn = LocalDate.of(2026, 8, 10);
        PageRequest pageable = PageRequest.of(1, 20);
        TaxCode taxCode = taxCode("ES", "IVA21", new BigDecimal("21"), true,
                LocalDate.of(2026, 1, 1), null);
        when(repository.search(companyId, "iva", "ES", true, validOn, pageable))
                .thenReturn(new PageImpl<>(List.of(taxCode), pageable, 1));

        Page<TaxCodeResponse> response = service.search(" iva ", " es ", true, validOn, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().code()).isEqualTo("IVA21");
        verify(repository).search(companyId, "iva", "ES", true, validOn, pageable);
    }

    private TaxCodeRequest request(String country, String code, BigDecimal percentage, LocalDate validFrom,
                                   LocalDate validUntil, boolean exempt, boolean active) {
        return new TaxCodeRequest(country, code, " Impuesto general ", percentage, validFrom, validUntil, exempt,
                active);
    }

    private TaxCode taxCode(String country, String code, BigDecimal percentage, boolean active,
                            LocalDate validFrom, LocalDate validUntil) {
        return new TaxCode(companyId, country, code, "Impuesto", percentage, validFrom, validUntil, false, active);
    }
}
