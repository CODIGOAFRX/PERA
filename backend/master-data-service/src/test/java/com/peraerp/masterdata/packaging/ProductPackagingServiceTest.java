package com.peraerp.masterdata.packaging;

import com.peraerp.masterdata.catalog.Product;
import com.peraerp.masterdata.catalog.ProductRepository;
import com.peraerp.masterdata.catalog.UnitOfMeasure;
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
class ProductPackagingServiceTest {
    @Mock ProductPackagingRepository repository;
    @Mock PackagingTypeRepository packagingTypes;
    @Mock ProductRepository products;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID typeId = UUID.randomUUID();
    private ProductPackagingService service;

    @BeforeEach
    void setUp() {
        service = new ProductPackagingService(repository, packagingTypes, products, companyProvider);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
    }

    @Test
    void createsNormalizedDefaultPackagingWithActiveReferences() {
        stubReferences(product(true), type(true, new BigDecimal("1"), new BigDecimal("20")));
        when(repository.existsByCompanyIdAndCodeIgnoreCase(companyId, "BOX-12")).thenReturn(false);
        when(repository.existsByCompanyIdAndProductIdAndDefaultPackagingTrueAndActiveTrue(companyId, productId))
                .thenReturn(false);
        when(repository.save(any(ProductPackaging.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductPackagingResponse response = service.create(request(" box-12 ", new BigDecimal("12"), 3,
                new BigDecimal("4"), new BigDecimal("8"), true, true));

        assertThat(response.code()).isEqualTo("BOX-12");
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.packagingTypeId()).isEqualTo(typeId);
        assertThat(response.defaultPackaging()).isTrue();
    }

    @Test
    void rejectsProductFromAnotherTenant() {
        when(products.findByIdAndCompanyId(productId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(null, BigDecimal.TEN, null, null, null, false, true)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(packagingTypes, never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    void rejectsInactivePackagingTypeForActiveOption() {
        stubReferences(product(true), type(false, null, null));

        assertThatThrownBy(() -> service.create(request(null, BigDecimal.TEN, null, null, null, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    void enforcesSingleActiveDefaultPerProduct() {
        stubReferences(product(true), type(true, null, null));
        when(repository.existsByCompanyIdAndProductIdAndDefaultPackagingTrueAndActiveTrue(companyId, productId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(null, BigDecimal.TEN, null, null, null, true, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("por defecto");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInactiveDefaultPackaging() {
        stubReferences(product(true), type(true, null, null));

        assertThatThrownBy(() -> service.create(request(null, BigDecimal.TEN, null, null, null, true, false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("debe estar activo");
    }

    @Test
    void rejectsInconsistentLevelsAndUnits() {
        stubReferences(product(true), type(true, null, null));

        assertThatThrownBy(() -> service.create(request(null, new BigDecimal("10"), 3,
                new BigDecimal("4"), null, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("coincidir");
    }

    @Test
    void validatesGrossWeightAgainstTareAndCapacity() {
        stubReferences(product(true), type(true, new BigDecimal("2"), new BigDecimal("10")));

        assertThatThrownBy(() -> service.create(request(null, BigDecimal.TEN, null, null,
                BigDecimal.ONE, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("tara");

        assertThatThrownBy(() -> service.create(request(null, BigDecimal.TEN, null, null,
                new BigDecimal("11"), false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("capacidad");
    }

    @Test
    void keepsCodeProductAndTypeImmutable() {
        UUID packagingId = UUID.randomUUID();
        ProductPackaging packaging = packaging(packagingId, "BOX-12", true, false);
        when(repository.findByIdAndCompanyId(packagingId, companyId)).thenReturn(Optional.of(packaging));

        assertThatThrownBy(() -> service.update(packagingId,
                request("OTHER", new BigDecimal("12"), null, null, null, false, true)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no se puede modificar");
    }

    @Test
    void allowsDeactivationWhenPreviouslyReferencedParentsAreInactive() {
        UUID packagingId = UUID.randomUUID();
        ProductPackaging packaging = packaging(packagingId, null, true, false);
        when(repository.findByIdAndCompanyId(packagingId, companyId)).thenReturn(Optional.of(packaging));
        stubReferences(product(false), type(false, null, null));

        ProductPackagingResponse response = service.update(packagingId,
                request(null, new BigDecimal("12"), null, null, null, false, false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void searchesWithTenantAndAllFilters() {
        UUID filterProductId = UUID.randomUUID();
        UUID filterTypeId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(1, 15);
        when(repository.search(companyId, "box", filterProductId, filterTypeId, true, true, pageable))
                .thenReturn(new PageImpl<>(List.of(packaging(UUID.randomUUID(), "BOX", true, true)), pageable, 1));

        assertThat(service.search(" box ", filterProductId, filterTypeId, true, true, pageable).getContent())
                .hasSize(1);
        verify(repository).search(companyId, "box", filterProductId, filterTypeId, true, true, pageable);
    }

    private void stubReferences(Product product, PackagingType type) {
        when(products.findByIdAndCompanyId(productId, companyId)).thenReturn(Optional.of(product));
        when(packagingTypes.findByIdAndCompanyId(typeId, companyId)).thenReturn(Optional.of(type));
    }

    private ProductPackagingRequest request(String code, BigDecimal units, Integer levels,
                                            BigDecimal unitsPerLevel, BigDecimal grossWeight,
                                            boolean defaultPackaging, boolean active) {
        return new ProductPackagingRequest(productId, typeId, code, units, levels, unitsPerLevel,
                new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("6"), grossWeight,
                defaultPackaging, active);
    }

    private Product product(boolean active) {
        Product product = new Product(companyId, "P", "Product", null, null, null, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"));
        ReflectionTestUtils.setField(product, "id", productId);
        if (!active) {
            product.update("Product", null, null, null, null, UnitOfMeasure.UNIT, BigDecimal.TEN,
                    new BigDecimal("21"), false);
        }
        return product;
    }

    private PackagingType type(boolean active, BigDecimal tare, BigDecimal maximumWeight) {
        PackagingType type = new PackagingType(companyId, "BOX", "Box", null, null, null, null,
                null, null, null, tare, maximumWeight, null, false, active);
        ReflectionTestUtils.setField(type, "id", typeId);
        return type;
    }

    private ProductPackaging packaging(UUID id, String code, boolean active, boolean defaultPackaging) {
        ProductPackaging packaging = new ProductPackaging(companyId, productId, typeId, code,
                new BigDecimal("12"), null, null, null, null, null, null, defaultPackaging, active);
        ReflectionTestUtils.setField(packaging, "id", id);
        return packaging;
    }
}
