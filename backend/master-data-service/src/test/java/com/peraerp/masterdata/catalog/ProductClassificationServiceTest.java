package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductClassificationServiceTest {
    @Mock ProductNatureRepository natures;
    @Mock ProductSupertypeRepository supertypes;
    @Mock ProductTypeRepository types;
    @Mock ProductGroupRepository groups;
    @Mock ProductRepository products;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private ProductClassificationService service;

    @BeforeEach
    void setUp() {
        service = new ProductClassificationService(natures, supertypes, types, groups, products, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsNormalizedNatureWithRequestedState() {
        when(natures.existsByCompanyIdAndCodeIgnoreCase(companyId, "MATERIAL")).thenReturn(false);
        when(natures.save(any(ProductNature.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductNatureResponse response = service.createNature(
                new ProductNatureRequest(" material ", " Materias primas ", false));

        assertThat(response.code()).isEqualTo("MATERIAL");
        assertThat(response.name()).isEqualTo("Materias primas");
        assertThat(response.active()).isFalse();
    }

    @Test
    void rejectsDuplicateNatureCodeWithinCurrentCompany() {
        when(natures.existsByCompanyIdAndCodeIgnoreCase(companyId, "MATERIAL")).thenReturn(true);

        assertThatThrownBy(() -> service.createNature(
                new ProductNatureRequest("material", "Materias primas", true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("MATERIAL");

        verify(natures, never()).save(any());
    }

    @Test
    void requiresNatureFromCurrentCompanyWhenCreatingSupertype() {
        UUID foreignNatureId = UUID.randomUUID();
        when(natures.findByIdAndCompanyId(foreignNatureId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createSupertype(
                new ProductSupertypeRequest("ST", "Supertipo", foreignNatureId, true)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(supertypes, never()).save(any());
    }

    @Test
    void requiresSupertypeFromCurrentCompanyWhenCreatingType() {
        UUID foreignSupertypeId = UUID.randomUUID();
        when(supertypes.findByIdAndCompanyId(foreignSupertypeId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createType(
                new ProductTypeRequest("TYPE", "Tipo", foreignSupertypeId, true)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(types, never()).save(any());
    }

    @Test
    void requiresTypeFromCurrentCompanyWhenCreatingGroup() {
        UUID foreignTypeId = UUID.randomUUID();
        when(types.findByIdAndCompanyId(foreignTypeId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createGroup(
                new ProductGroupRequest("GROUP", "Grupo", foreignTypeId, true)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(groups, never()).save(any());
    }

    @Test
    void keepsTypeCodeImmutableWhileAllowingParentAndStateChanges() {
        UUID typeId = UUID.randomUUID();
        UUID oldSupertypeId = UUID.randomUUID();
        UUID newSupertypeId = UUID.randomUUID();
        ProductType type = new ProductType(companyId, oldSupertypeId, "TYPE", "Anterior", true);
        when(types.findByIdAndCompanyId(typeId, companyId)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.updateType(typeId,
                new ProductTypeRequest("OTHER", "Nuevo", newSupertypeId, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");

        verify(supertypes, never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    void preventsMovingAnAssignedGroupToAnotherType() {
        UUID groupId = UUID.randomUUID();
        UUID oldTypeId = UUID.randomUUID();
        UUID newTypeId = UUID.randomUUID();
        ProductGroup group = new ProductGroup(companyId, oldTypeId, "GROUP", "Grupo", true);
        ProductType newType = new ProductType(companyId, UUID.randomUUID(), "TYPE2", "Tipo nuevo", true);
        when(groups.findByIdAndCompanyId(groupId, companyId)).thenReturn(Optional.of(group));
        when(types.findByIdAndCompanyId(newTypeId, companyId)).thenReturn(Optional.of(newType));
        when(products.existsByCompanyIdAndProductGroupId(companyId, groupId)).thenReturn(true);

        assertThatThrownBy(() -> service.updateGroup(groupId,
                new ProductGroupRequest("GROUP", "Grupo", newTypeId, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("asignado a artículos");
    }
}
