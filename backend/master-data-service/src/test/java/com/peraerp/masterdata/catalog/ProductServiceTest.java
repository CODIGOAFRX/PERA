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
class ProductServiceTest {
    @Mock ProductRepository products;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, companyProvider);
    }

    @Test
    void createsNormalizedProduct() {
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(products.existsByCompanyIdAndCodeIgnoreCase(companyId, "A001")).thenReturn(false);
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = service.create(request(" a001 ", " Servicio mensual ", true));

        assertThat(response.code()).isEqualTo("A001");
        assertThat(response.name()).isEqualTo("Servicio mensual");
        assertThat(response.basePrice()).isEqualByComparingTo("49.90");
        assertThat(response.active()).isTrue();
    }

    @Test
    void updatesMutableProductFields() {
        UUID id = UUID.randomUUID();
        Product product = new Product(companyId, "A001", "Anterior", null, null, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"));
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(products.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(product));

        ProductResponse response = service.update(id, request("A001", "Actualizado", false));

        assertThat(response.name()).isEqualTo("Actualizado");
        assertThat(response.active()).isFalse();
        assertThat(response.basePrice()).isEqualByComparingTo("49.90");
    }

    @Test
    void rejectsProductCodeChanges() {
        UUID id = UUID.randomUUID();
        Product product = new Product(companyId, "A001", "Producto", null, null, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"));
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(products.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.update(id, request("OTHER", "Producto", true)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reportsMissingProductWithinCurrentCompany() {
        UUID id = UUID.randomUUID();
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(products.findByIdAndCompanyId(id, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    private ProductRequest request(String code, String name, boolean active) {
        return new ProductRequest(code, name, "Descripción", null, null, null, UnitOfMeasure.UNIT,
                new BigDecimal("49.90"), new BigDecimal("21"), active);
    }
}
