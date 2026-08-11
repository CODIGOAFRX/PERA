package com.peraerp.sales.masterdata;

import com.peraerp.platform.domain.BusinessRuleException;
import com.peraerp.sales.document.DocumentLineRequest;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class SalesMasterDataService {
    private static final int MAX_TRACE_BYTES = 65_536;
    private final MasterDataClient client;
    private final ObjectMapper objectMapper;

    public SalesMasterDataService(MasterDataClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public CustomerSnapshot requireActiveCustomer(UUID customerId) {
        CustomerSnapshot customer = client.findCustomer(customerId);
        if (!customer.active()) throw new BusinessRuleException("El cliente del documento está inactivo.");
        return customer;
    }

    public ResolvedDocumentLine resolveLine(UUID customerId, DocumentLineRequest line,
                                            LocalDate issueDate, String currency) {
        if (line.productId() == null) {
            return new ResolvedDocumentLine(null, nullableTrim(line.productCode()), line.description().trim(),
                    line.quantity(), line.quantity(), line.unitPrice(), defaultZero(line.discountPercentage()),
                    defaultZero(line.taxPercentage()), null, null, null, null);
        }
        ProductSnapshot product = client.findProduct(line.productId());
        if (!product.active()) throw new BusinessRuleException("El producto " + product.code() + " está inactivo.");
        TaxCodeSnapshot taxCode = line.taxPercentageOverridden() ? null : resolveTaxCode(product, issueDate);
        BigDecimal taxPercentage = line.taxPercentageOverridden()
                ? defaultZero(line.taxPercentage())
                : taxCode == null ? product.taxRate() : taxCode.percentage();
        if (line.unitPriceOverridden()) {
            return new ResolvedDocumentLine(product.id(), product.code(), line.description().trim(), line.quantity(),
                    line.quantity(), line.unitPrice(), defaultZero(line.discountPercentage()), taxPercentage,
                    null, null, null, null, taxCode == null ? null : taxCode.id(),
                    taxCode == null ? null : taxCode.code(), taxCode == null ? null : taxCode.countryCode(),
                    taxCode == null ? null : taxCode.name(), taxCode == null ? null : taxCode.exempt());
        }
        PricingSnapshot price = client.resolvePrice(customerId, product.id(), line.quantity(), issueDate,
                product.basePrice(), currency.trim().toUpperCase(Locale.ROOT));
        if (price.billedQuantity().signum() <= 0 || price.finalPrice().signum() < 0) {
            throw new BusinessRuleException("La tarifa resolvió cantidades o importes no válidos.");
        }
        BigDecimal displayUnitPrice = price.finalPrice().divide(price.billedQuantity(), 4, RoundingMode.HALF_UP);
        return new ResolvedDocumentLine(product.id(), product.code(), line.description().trim(), line.quantity(),
                price.billedQuantity(), displayUnitPrice, defaultZero(line.discountPercentage()),
                taxPercentage, price.tariffId(), price.tariffCode(),
                price.finalPrice(), serializeTrace(price), taxCode == null ? null : taxCode.id(),
                taxCode == null ? null : taxCode.code(), taxCode == null ? null : taxCode.countryCode(),
                taxCode == null ? null : taxCode.name(), taxCode == null ? null : taxCode.exempt());
    }

    private TaxCodeSnapshot resolveTaxCode(ProductSnapshot product, LocalDate issueDate) {
        if (product.taxCodeId() == null) return null;
        TaxCodeSnapshot taxCode = client.findTaxCode(product.taxCodeId());
        if (!taxCode.isApplicableOn(issueDate)) {
            throw new BusinessRuleException("El código fiscal " + taxCode.code()
                    + " no está activo o vigente en la fecha del documento.");
        }
        return taxCode;
    }

    private String serializeTrace(PricingSnapshot price) {
        try {
            String json = objectMapper.writeValueAsString(price);
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TRACE_BYTES) {
                throw new BusinessRuleException("La traza de tarifa supera 64 KiB.");
            }
            return json;
        } catch (JacksonException exception) {
            throw new BusinessRuleException("No se pudo guardar la traza de tarifa.", exception);
        }
    }

    private BigDecimal defaultZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String nullableTrim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
