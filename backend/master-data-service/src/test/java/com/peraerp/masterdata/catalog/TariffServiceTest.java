package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

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
class TariffServiceTest {
    @Mock PriceListRepository tariffs;
    @Mock PriceListItemRepository items;
    @Mock PricingRuleRepository rules;
    @Mock PricingReferenceService references;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private TariffService service;

    @BeforeEach
    void setUp() {
        service = new TariffService(tariffs, items, rules, references, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsNormalizedTenantTariff() {
        TariffRequest request = generalRequest(" base ", null);
        when(tariffs.existsByCompanyIdAndCodeIgnoreCase(companyId, "BASE")).thenReturn(false);
        when(tariffs.findAllByCompanyIdAndScope(companyId, PricingScope.GENERAL)).thenReturn(List.of());
        when(tariffs.save(any(PriceList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TariffResponse response = service.createTariff(request);

        assertThat(response.code()).isEqualTo("BASE");
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.scope()).isEqualTo(PricingScope.GENERAL);
        verify(references).validateTariffTarget(companyId, request);
    }

    @Test
    void keepsTariffCodeImmutable() {
        UUID tariffId = UUID.randomUUID();
        when(tariffs.findByIdAndCompanyId(tariffId, companyId)).thenReturn(Optional.of(tariff("BASE", null)));

        assertThatThrownBy(() -> service.updateTariff(tariffId, generalRequest("OTHER", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");

        verify(references, never()).validateTariffTarget(any(), any());
    }

    @Test
    void rejectsParentCycles() {
        UUID tariffId = UUID.randomUUID();
        when(tariffs.findByIdAndCompanyId(tariffId, companyId)).thenReturn(Optional.of(tariff("BASE", null)));

        assertThatThrownBy(() -> service.updateTariff(tariffId, generalRequest("BASE", tariffId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ciclos");
    }

    @Test
    void rejectsParentFromAnotherTenantWithoutLeakingIt() {
        UUID parentId = UUID.randomUUID();
        when(tariffs.existsByCompanyIdAndCodeIgnoreCase(companyId, "CHILD")).thenReturn(false);
        when(tariffs.findByIdAndCompanyId(parentId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTariff(generalRequest("CHILD", parentId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsOverlappingTariffsWithSameTargetAndPriority() {
        PriceList existing = tariff("BASE", null);
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        when(tariffs.existsByCompanyIdAndCodeIgnoreCase(companyId, "OTHER")).thenReturn(false);
        when(tariffs.findAllByCompanyIdAndScope(companyId, PricingScope.GENERAL)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.createTariff(generalRequest("OTHER", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mismo").hasMessageContaining("vigencia");

        verify(tariffs, never()).save(any());
    }

    @Test
    void createsExplicitProductAndCustomerPrice() {
        UUID tariffId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        TariffItemRequest request = new TariffItemRequest(productId, customerId, new BigDecimal("42.50"),
                new BigDecimal("7.5"), BigDecimal.ZERO, 20, LocalDate.of(2026, 1, 1), null, true);
        when(tariffs.findByIdAndCompanyId(tariffId, companyId)).thenReturn(Optional.of(tariff("BASE", null)));
        when(items.findAllByCompanyIdAndPriceListId(companyId, tariffId)).thenReturn(List.of());
        when(rules.findAllByCompanyIdAndPriceListId(companyId, tariffId)).thenReturn(List.of());
        when(items.save(any(PriceListItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TariffItemResponse response = service.createItem(tariffId, request);

        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.price()).isEqualByComparingTo("42.50");
        assertThat(response.discountPercentage()).isEqualByComparingTo("7.5");
        verify(references).requireProduct(productId, companyId);
        verify(references).requireCustomer(customerId, companyId);
    }

    @Test
    void rejectsAmbiguousProductRuleAgainstExistingItem() {
        UUID tariffId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        PriceListItem item = new PriceListItem(companyId, tariffId, productId, null, new BigDecimal("12"),
                BigDecimal.ZERO, BigDecimal.ZERO, 5, LocalDate.of(2026, 1, 1), null, true);
        PricingRuleRequest request = new PricingRuleRequest(PricingTargetType.PRODUCT, null, null, null, null,
                productId, null, new BigDecimal("11"), BigDecimal.ZERO, BigDecimal.ZERO, 5,
                LocalDate.of(2026, 2, 1), null, true);
        when(tariffs.findByIdAndCompanyId(tariffId, companyId)).thenReturn(Optional.of(tariff("BASE", null)));
        when(rules.findAllByCompanyIdAndPriceListId(companyId, tariffId)).thenReturn(List.of());
        when(items.findAllByCompanyIdAndPriceListId(companyId, tariffId)).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.createRule(tariffId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ambigua");
    }

    @Test
    void rejectsInvalidCommercialRanges() {
        TariffRequest request = new TariffRequest("BASE", "Base", "EUR", LocalDate.of(2026, 1, 1), null,
                true, 0, PricingScope.GENERAL, null, null, null, null, null, null, null,
                new BigDecimal("101"), null, null, null, null);

        assertThatThrownBy(() -> service.createTariff(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("entre 0 y 100");
    }

    @Test
    void searchesUsingCurrentTenantAndAllRequestedFilters() {
        UUID customerId = UUID.randomUUID();
        UUID natureId = UUID.randomUUID();
        UUID supertypeId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 8, 10);
        PageRequest pageable = PageRequest.of(0, 20);
        PriceList result = tariff("BASE", null);
        when(tariffs.search(companyId, "base", customerId, natureId, supertypeId, typeId,
                PricingScope.PRODUCT_TYPE, true, date, pageable))
                .thenReturn(new PageImpl<>(List.of(result), pageable, 1));

        assertThat(service.searchTariffs(" base ", customerId, natureId, supertypeId, typeId,
                PricingScope.PRODUCT_TYPE, true, date, pageable).getContent()).hasSize(1);
        verify(tariffs).search(companyId, "base", customerId, natureId, supertypeId, typeId,
                PricingScope.PRODUCT_TYPE, true, date, pageable);
    }

    private TariffRequest generalRequest(String code, UUID parentId) {
        return new TariffRequest(code, " Tarifa base ", " eur ", LocalDate.of(2026, 1, 1), null, true, 10,
                PricingScope.GENERAL, null, null, null, null, null, null, parentId,
                new BigDecimal("2.5"), null, new BigDecimal("10"), new BigDecimal("1"), null);
    }

    private PriceList tariff(String code, UUID parentId) {
        return new PriceList(companyId, code, "Tarifa", "EUR", LocalDate.of(2026, 1, 1), null, true, 10,
                PricingScope.GENERAL, null, null, null, null, null, null, parentId,
                null, null, null, null, null);
    }
}
