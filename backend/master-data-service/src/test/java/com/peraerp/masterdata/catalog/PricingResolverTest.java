package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingResolverTest {
    @Mock PriceListRepository tariffs;
    @Mock PriceListItemRepository items;
    @Mock PricingRuleRepository rules;
    @Mock PricingReferenceService references;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final LocalDate date = LocalDate.of(2026, 8, 10);
    private PricingResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PricingResolver(tariffs, items, rules, references, companyProvider);
        lenient().when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void returnsBasePriceWhenNoTariffMatches() {
        PricingResolveRequest request = request(new BigDecimal("2"), new BigDecimal("12"));
        when(references.resolveContext(companyId, request)).thenReturn(context(null, new BigDecimal("2"),
                new BigDecimal("12")));
        when(tariffs.findResolutionCandidates(companyId, "EUR", date)).thenReturn(List.of());

        PricingResolveResponse response = resolver.resolve(request);

        assertThat(response.tariffId()).isNull();
        assertThat(response.finalUnitPrice()).isEqualByComparingTo("12.0000");
        assertThat(response.finalPrice()).isEqualByComparingTo("24.0000");
        assertThat(response.trace()).extracting(PricingTraceStep::operation).containsExactly("BASE_PRICE");
    }

    @Test
    void preservesLegacyCustomerPriceListAssignment() {
        UUID assignedId = UUID.randomUUID();
        PriceList assigned = tariff(assignedId, "ASSIGNED", 0, PricingScope.GENERAL, null, null,
                null, null, null, null);
        PriceList highPriority = tariff(UUID.randomUUID(), "HIGH", 100, PricingScope.GENERAL, null, null,
                null, null, null, null);
        PricingResolveRequest request = request(BigDecimal.ONE, BigDecimal.TEN);
        when(references.resolveContext(companyId, request)).thenReturn(context(assignedId, BigDecimal.ONE,
                BigDecimal.TEN));
        when(tariffs.findResolutionCandidates(companyId, "EUR", date)).thenReturn(List.of(highPriority, assigned));
        when(items.findAllByCompanyIdAndPriceListId(companyId, assignedId)).thenReturn(List.of());
        when(rules.findAllByCompanyIdAndPriceListId(companyId, assignedId)).thenReturn(List.of());

        PricingResolveResponse response = resolver.resolve(request);

        assertThat(response.tariffId()).isEqualTo(assignedId);
        assertThat(response.tariffCode()).isEqualTo("ASSIGNED");
    }

    @Test
    void resolvesInheritanceRulesMultiplesSurchargesAndMinimumsWithDeterministicTrace() {
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        PriceList parent = tariff(parentId, "PARENT", 0, PricingScope.GENERAL, null, null,
                new BigDecimal("10"), null, null, new BigDecimal("2"));
        PriceList child = tariff(childId, "CHILD", 30, PricingScope.PRODUCT, productId, parentId,
                null, new BigDecimal("5"), new BigDecimal("50"), null);
        child.update(child.getName(), child.getCurrency(), child.getValidFrom(), child.getValidUntil(), true,
                child.getPriority(), child.getScope(), null, null, null, null, null, productId, parentId,
                null, new BigDecimal("5"), new BigDecimal("50"), null, new BigDecimal("9"));
        PriceListItem item = new PriceListItem(companyId, childId, productId, null, new BigDecimal("8"),
                new BigDecimal("10"), new BigDecimal("5"), 20, LocalDate.of(2026, 1, 1), null, true);
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        PricingResolveRequest request = request(new BigDecimal("3"), BigDecimal.TEN);
        when(references.resolveContext(companyId, request)).thenReturn(context(null, new BigDecimal("3"),
                BigDecimal.TEN));
        when(tariffs.findResolutionCandidates(companyId, "EUR", date)).thenReturn(List.of(child));
        when(tariffs.findByIdAndCompanyId(parentId, companyId)).thenReturn(Optional.of(parent));
        when(items.findAllByCompanyIdAndPriceListId(companyId, parentId)).thenReturn(List.of());
        when(items.findAllByCompanyIdAndPriceListId(companyId, childId)).thenReturn(List.of(item));
        when(rules.findAllByCompanyIdAndPriceListId(companyId, parentId)).thenReturn(List.of());
        when(rules.findAllByCompanyIdAndPriceListId(companyId, childId)).thenReturn(List.of());

        PricingResolveResponse response = resolver.resolve(request);

        assertThat(response.billedQuantity()).isEqualByComparingTo("4");
        assertThat(response.finalUnitPrice()).isEqualByComparingTo("9.0000");
        assertThat(response.subtotal()).isEqualByComparingTo("36.0000");
        assertThat(response.finalPrice()).isEqualByComparingTo("50.0000");
        assertThat(response.trace()).extracting(PricingTraceStep::operation).containsExactly(
                "INHERITED_TARIFF", "SELECTED_TARIFF", "BASE_PRICE", "FIXED_PRICE", "DISCOUNT",
                "RULE_SURCHARGE", "MINIMUM_PER_PIECE", "UNIT_MULTIPLE", "GENERAL_SURCHARGE",
                "ENERGY_SURCHARGE", "MINIMUM_BILLING");
    }

    @Test
    void choosesMostSpecificRuleAtEqualPriority() {
        UUID tariffId = UUID.randomUUID();
        UUID natureId = UUID.randomUUID();
        PriceList tariff = tariff(tariffId, "BASE", 0, PricingScope.GENERAL, null, null,
                null, null, null, null);
        PricingRule natureRule = new PricingRule(companyId, tariffId, PricingTargetType.PRODUCT_NATURE,
                natureId, null, null, null, null, null, new BigDecimal("15"), BigDecimal.ZERO,
                BigDecimal.ZERO, 5, LocalDate.of(2026, 1, 1), null, true);
        PricingRule productRule = new PricingRule(companyId, tariffId, PricingTargetType.PRODUCT,
                null, null, null, null, productId, null, new BigDecimal("11"), BigDecimal.ZERO,
                BigDecimal.ZERO, 5, LocalDate.of(2026, 1, 1), null, true);
        ReflectionTestUtils.setField(natureRule, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(productRule, "id", UUID.randomUUID());
        PricingResolveRequest request = request(BigDecimal.ONE, new BigDecimal("20"));
        PricingContext context = new PricingContext(companyId, null, null, productId, natureId, null, null, null,
                BigDecimal.ONE, date, new BigDecimal("20"), "EUR");
        when(references.resolveContext(companyId, request)).thenReturn(context);
        when(tariffs.findResolutionCandidates(companyId, "EUR", date)).thenReturn(List.of(tariff));
        when(items.findAllByCompanyIdAndPriceListId(companyId, tariffId)).thenReturn(List.of());
        when(rules.findAllByCompanyIdAndPriceListId(companyId, tariffId))
                .thenReturn(List.of(natureRule, productRule));

        PricingResolveResponse response = resolver.resolve(request);

        assertThat(response.finalUnitPrice()).isEqualByComparingTo("11.0000");
        assertThat(response.trace()).filteredOn(step -> step.operation().equals("FIXED_PRICE"))
                .singleElement().satisfies(step -> assertThat(step.description()).contains("PRODUCT"));
    }

    @Test
    void rejectsCorruptInheritanceCycleAtResolutionTime() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        PriceList first = tariff(firstId, "FIRST", 1, PricingScope.GENERAL, null, secondId,
                null, null, null, null);
        PriceList second = tariff(secondId, "SECOND", 1, PricingScope.GENERAL, null, firstId,
                null, null, null, null);
        PricingResolveRequest request = request(BigDecimal.ONE, BigDecimal.ONE);
        when(references.resolveContext(companyId, request)).thenReturn(context(null, BigDecimal.ONE,
                BigDecimal.ONE));
        when(tariffs.findResolutionCandidates(companyId, "EUR", date)).thenReturn(List.of(first));
        when(tariffs.findByIdAndCompanyId(secondId, companyId)).thenReturn(Optional.of(second));
        when(tariffs.findByIdAndCompanyId(firstId, companyId)).thenReturn(Optional.of(first));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ciclo");
    }

    @Test
    void rejectsInvalidQuantityBeforeReadingTenantData() {
        PricingResolveRequest request = request(BigDecimal.ZERO, BigDecimal.TEN);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cantidad");
    }

    private PricingResolveRequest request(BigDecimal quantity, BigDecimal basePrice) {
        return new PricingResolveRequest(null, productId, null, null, null, null, quantity, date, basePrice,
                "EUR");
    }

    private PricingContext context(UUID assignedTariffId, BigDecimal quantity, BigDecimal basePrice) {
        return new PricingContext(companyId, null, assignedTariffId, productId, null, null, null, null,
                quantity, date, basePrice, "EUR");
    }

    private PriceList tariff(UUID id, String code, int priority, PricingScope scope, UUID scopedProductId,
                             UUID parentId, BigDecimal generalSurcharge, BigDecimal energySurcharge,
                             BigDecimal minimumBilling, BigDecimal unitMultiple) {
        PriceList tariff = new PriceList(companyId, code, code, "EUR", LocalDate.of(2026, 1, 1), null, true,
                priority, scope, null, null, null, null, null, scopedProductId, parentId, generalSurcharge,
                energySurcharge, minimumBilling, unitMultiple, null);
        ReflectionTestUtils.setField(tariff, "id", id);
        return tariff;
    }
}
