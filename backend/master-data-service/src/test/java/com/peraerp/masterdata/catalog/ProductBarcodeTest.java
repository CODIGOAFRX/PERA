package com.peraerp.masterdata.catalog;

import com.peraerp.masterdata.config.CurrentCompanyProvider;
import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.platform.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Código de barras del artículo y búsqueda de lo que lee un escáner. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductBarcodeTest {
    @Mock ProductRepository products;
    @Mock CurrentCompanyProvider companyProvider;

    private final UUID companyId = UUID.randomUUID();
    private ProductService service;
    private Product existing;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, companyProvider);
        existing = new Product(companyId, "SARD", "Sardinas", null, null, null, null, UnitOfMeasure.UNIT,
                BigDecimal.ONE, new BigDecimal("10"));
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.changeBarcode("8410000000017");
        when(companyProvider.requireCompanyId()).thenReturn(companyId);
        when(products.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(products.findByIdAndCompanyId(existing.getId(), companyId)).thenReturn(Optional.of(existing));
        when(products.findFirstByCompanyIdAndBarcodeIgnoreCase(eq(companyId), any())).thenReturn(Optional.empty());
        when(products.findFirstByCompanyIdAndBarcodeIgnoreCase(companyId, "8410000000017"))
                .thenReturn(Optional.of(existing));
        when(products.findFirstByCompanyIdAndCodeIgnoreCase(eq(companyId), any())).thenReturn(Optional.empty());
        when(products.findFirstByCompanyIdAndCodeIgnoreCase(companyId, "SARD")).thenReturn(Optional.of(existing));
    }

    @Test
    void twoProductsCannotShareABarcode() {
        assertThatThrownBy(() -> service.create(request("ATUN", " 8410000000017 ")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("SARD");
        assertThat(service.create(request("ATUN", "8410000000024")).barcode()).isEqualTo("8410000000024");
    }

    @Test
    void anOldClientThatDoesNotSendTheBarcodeKeepsItAndAnEmptyOneRemovesIt() {
        assertThat(service.update(existing.getId(), request("SARD", null)).barcode()).isEqualTo("8410000000017");
        assertThat(service.update(existing.getId(), request("SARD", "8410000000017")).barcode())
                .isEqualTo("8410000000017");
        assertThat(service.update(existing.getId(), request("SARD", "")).barcode()).isNull();
    }

    @Test
    void aScannedCodeIsLookedUpAsBarcodeFirstAndThenAsProductCode() {
        assertThat(service.lookup(" 8410000000017 ").code()).isEqualTo("SARD");
        assertThat(service.lookup("SARD").code()).isEqualTo("SARD");
        assertThatThrownBy(() -> service.lookup("0000")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.lookup("  ")).isInstanceOf(BusinessRuleException.class);
    }

    private ProductRequest request(String code, String barcode) {
        return new ProductRequest(code, "Producto", null, null, null, null, null, null, UnitOfMeasure.UNIT,
                new BigDecimal("1.50"), new BigDecimal("10"), true, barcode);
    }
}
