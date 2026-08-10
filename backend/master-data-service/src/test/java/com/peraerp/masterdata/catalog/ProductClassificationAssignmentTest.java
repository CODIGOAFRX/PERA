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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductClassificationAssignmentTest {
    @Mock ProductRepository products;
    @Mock ProductTypeRepository types;
    @Mock ProductGroupRepository groups;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, companyProvider, types, groups);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void derivesProductTypeFromSelectedGroup() {
        UUID typeId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ProductGroup group = new ProductGroup(companyId, typeId, "GROUP", "Grupo", true);
        ProductType type = new ProductType(companyId, UUID.randomUUID(), "TYPE", "Tipo", true);
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "P001")).thenReturn(false);
        when(groups.findByIdAndCompanyId(groupId, companyId)).thenReturn(Optional.of(group));
        when(types.findByIdAndCompanyId(typeId, companyId)).thenReturn(Optional.of(type));
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.create(request(null, groupId));

        assertThat(response.productTypeId()).isEqualTo(typeId);
        assertThat(response.productGroupId()).isEqualTo(groupId);
    }

    @Test
    void rejectsTypeThatDoesNotOwnSelectedGroup() {
        UUID groupTypeId = UUID.randomUUID();
        UUID requestedTypeId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        ProductGroup group = new ProductGroup(companyId, groupTypeId, "GROUP", "Grupo", true);
        ProductType groupType = new ProductType(companyId, UUID.randomUUID(), "TYPE", "Tipo", true);
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "P001")).thenReturn(false);
        when(groups.findByIdAndCompanyId(groupId, companyId)).thenReturn(Optional.of(group));
        when(types.findByIdAndCompanyId(groupTypeId, companyId)).thenReturn(Optional.of(groupType));

        assertThatThrownBy(() -> service.create(request(requestedTypeId, groupId)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no pertenece");
    }

    @Test
    void rejectsGroupOutsideCurrentCompany() {
        UUID groupId = UUID.randomUUID();
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "P001")).thenReturn(false);
        when(groups.findByIdAndCompanyId(groupId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(null, groupId)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private ProductRequest request(UUID typeId, UUID groupId) {
        return new ProductRequest("P001", "Producto", null, typeId, groupId, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"), true);
    }
}
