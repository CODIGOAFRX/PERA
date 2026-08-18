package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductTaxCodeAssignmentTest {
    @Mock ProductRepository products;
    @Mock ProductTypeRepository types;
    @Mock ProductGroupRepository groups;
    @Mock TaxCodeRepository taxCodes;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private ProductService service;

    @BeforeEach
    void setUp() {
        TaxCodeService taxCodeService = new TaxCodeService(taxCodes, companyProvider);
        service = new ProductService(products, companyProvider, types, groups, taxCodeService);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void usesFiscalPercentageAsEffectiveProductRate() {
        UUID taxCodeId = UUID.randomUUID();
        TaxCode taxCode = applicableTaxCode(new BigDecimal("21"));
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "P001")).thenReturn(false);
        when(taxCodes.findByIdAndCompanyId(taxCodeId, companyId)).thenReturn(Optional.of(taxCode));
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.create(request(taxCodeId, new BigDecimal("5")));

        assertThat(response.taxCodeId()).isEqualTo(taxCodeId);
        assertThat(response.taxRate()).isEqualByComparingTo("21");
    }

    @Test
    void rejectsTaxCodeOutsideCurrentCompany() {
        UUID foreignTaxCodeId = UUID.randomUUID();
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "P001")).thenReturn(false);
        when(taxCodes.findByIdAndCompanyId(foreignTaxCodeId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(foreignTaxCodeId, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsInactiveOrExpiredTaxCode() {
        UUID taxCodeId = UUID.randomUUID();
        TaxCode expired = new TaxCode(companyId, "ES", "OLD", "Expirado", new BigDecimal("21"),
                LocalDate.now().minusYears(2), LocalDate.now().minusYears(1), false, true);
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "P001")).thenReturn(false);
        when(taxCodes.findByIdAndCompanyId(taxCodeId, companyId)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.create(request(taxCodeId, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("vigente");
    }

    @Test
    void preservesLegacyRateWhenNoTaxCodeIsAssigned() {
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "P001")).thenReturn(false);
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.create(request(null, new BigDecimal("10")));

        assertThat(response.taxCodeId()).isNull();
        assertThat(response.taxRate()).isEqualByComparingTo("10");
    }

    private ProductRequest request(UUID taxCodeId, BigDecimal legacyRate) {
        return new ProductRequest("P001", "Producto", null, null, null, taxCodeId, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, legacyRate, true);
    }

    private TaxCode applicableTaxCode(BigDecimal percentage) {
        return new TaxCode(companyId, "ES", "IVA21", "Impuesto general", percentage,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), false, true);
    }
}
