package com.peraerp.masterdata.packaging;

import com.peraerp.masterdata.catalog.Product;
import com.peraerp.masterdata.catalog.ProductGroupRepository;
import com.peraerp.masterdata.catalog.ProductRepository;
import com.peraerp.masterdata.catalog.ProductRequest;
import com.peraerp.masterdata.catalog.ProductService;
import com.peraerp.masterdata.catalog.ProductTypeRepository;
import com.peraerp.masterdata.catalog.TaxCodeService;
import com.peraerp.masterdata.catalog.UnitOfMeasure;
import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductPackagingLifecycleTest {
    @Mock ProductRepository products;
    @Mock CurrentCompanyProvider companyProvider;
    @Mock ProductTypeRepository types;
    @Mock ProductGroupRepository groups;
    @Mock TaxCodeService taxes;
    @Mock ProductPackagingRepository packaging;

    @Test
    void blocksProductDeactivationWhileItHasActivePackaging() {
        UUID companyId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = new Product(companyId, "P", "Product", null, null, null, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"));
        ReflectionTestUtils.setField(product, "id", productId);
        ProductService service = new ProductService(products, companyProvider, types, groups, taxes, packaging);
        ProductRequest request = new ProductRequest("P", "Product", null, null, null, null,
                UnitOfMeasure.UNIT, BigDecimal.TEN, new BigDecimal("21"), false);
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(products.findByIdAndCompanyId(productId, companyId)).thenReturn(Optional.of(product));
        when(packaging.existsByCompanyIdAndProductIdAndActiveTrue(companyId, productId)).thenReturn(true);

        assertThatThrownBy(() -> service.update(productId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("embalajes activos");
    }
}
