package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.customer.CustomerProfile;
import com.peraerp.masterdata.customer.CustomerProfileRepository;
import com.peraerp.masterdata.customer.RiskPolicy;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingReferenceServiceTest {
    @Mock CustomerProfileRepository customers;
    @Mock ProductNatureRepository natures;
    @Mock ProductSupertypeRepository supertypes;
    @Mock ProductTypeRepository types;
    @Mock ProductGroupRepository groups;
    @Mock ProductRepository products;

    private final UUID companyId = UUID.randomUUID();
    private PricingReferenceService service;

    @BeforeEach
    void setUp() {
        service = new PricingReferenceService(customers, natures, supertypes, types, groups, products);
    }

    @Test
    void rejectsCustomerOutsideCurrentTenant() {
        UUID customerId = UUID.randomUUID();
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveContext(companyId, request(customerId, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void preservesAssignedLegacyTariffInResolvedContext() {
        UUID customerId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        CustomerProfile customer = new CustomerProfile(companyId, UUID.randomUUID(), tariffId, null, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, RiskPolicy.WARN);
        when(customers.findByIdAndCompanyId(customerId, companyId)).thenReturn(Optional.of(customer));

        PricingContext context = service.resolveContext(companyId, request(customerId, null, null));

        assertThat(context.customerId()).isEqualTo(customerId);
        assertThat(context.assignedTariffId()).isEqualTo(tariffId);
    }

    @Test
    void derivesCompleteClassificationFromProduct() {
        UUID productId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID typeId = UUID.randomUUID();
        UUID supertypeId = UUID.randomUUID();
        UUID natureId = UUID.randomUUID();
        Product product = new Product(companyId, "P", "Product", null, typeId, groupId, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"));
        ProductGroup group = new ProductGroup(companyId, typeId, "G", "Group", true);
        ProductType type = new ProductType(companyId, supertypeId, "T", "Type", true);
        ProductSupertype supertype = new ProductSupertype(companyId, natureId, "S", "Supertype", true);
        ProductNature nature = new ProductNature(companyId, "N", "Nature", true);
        when(products.findByIdAndCompanyId(productId, companyId)).thenReturn(Optional.of(product));
        when(groups.findByIdAndCompanyId(groupId, companyId)).thenReturn(Optional.of(group));
        when(types.findByIdAndCompanyId(typeId, companyId)).thenReturn(Optional.of(type));
        when(supertypes.findByIdAndCompanyId(supertypeId, companyId)).thenReturn(Optional.of(supertype));
        when(natures.findByIdAndCompanyId(natureId, companyId)).thenReturn(Optional.of(nature));

        PricingContext context = service.resolveContext(companyId, request(null, productId, null));

        assertThat(context.productGroupId()).isEqualTo(groupId);
        assertThat(context.productTypeId()).isEqualTo(typeId);
        assertThat(context.productSupertypeId()).isEqualTo(supertypeId);
        assertThat(context.productNatureId()).isEqualTo(natureId);
    }

    @Test
    void rejectsClassificationThatContradictsProduct() {
        UUID productId = UUID.randomUUID();
        UUID actualTypeId = UUID.randomUUID();
        UUID suppliedTypeId = UUID.randomUUID();
        Product product = new Product(companyId, "P", "Product", null, actualTypeId, null, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"));
        when(products.findByIdAndCompanyId(productId, companyId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.resolveContext(companyId,
                request(null, productId, suppliedTypeId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no coincide");
    }

    @Test
    void rejectsTariffReferenceOutsideCurrentTenant() {
        UUID productId = UUID.randomUUID();
        TariffRequest request = new TariffRequest("P", "Product", "EUR", LocalDate.of(2026, 1, 1), null,
                true, 0, PricingScope.PRODUCT, null, null, null, null, null, productId, null,
                null, null, null, null, null);
        when(products.findByIdAndCompanyId(productId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateTariffTarget(companyId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PricingResolveRequest request(UUID customerId, UUID productId, UUID productTypeId) {
        return new PricingResolveRequest(customerId, productId, null, null, productTypeId, null, BigDecimal.ONE,
                LocalDate.of(2026, 8, 10), BigDecimal.TEN, "EUR");
    }
}
