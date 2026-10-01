package com.peraerp.operations.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record PurchaseLineRequest(
            UUID productId,
            @Size(max = 100) String productCode,
            @NotBlank @Size(max = 300) String description,
            @NotBlank @Size(max = 30) String unitOfMeasure,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal quantity,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal unitPrice,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal discountPercentage,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal taxPercentage
    ) {
    }

    public record PurchaseDocumentRequest(
            @NotNull PurchaseDocumentType type,
            @NotNull UUID supplierId,
            @NotBlank @Size(max = 40) String supplierCode,
            @NotBlank @Size(max = 180) String supplierName,
            @Size(max = 30) String supplierTaxId,
            @Size(max = 80) String supplierReference,
            @NotNull LocalDate issueDate,
            LocalDate expectedDate,
            UUID warehouseId,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currencyCode,
            @Size(max = 1000) String notes,
            @NotEmpty @Size(max = 500) List<@Valid @NotNull PurchaseLineRequest> lines
    ) {
    }

    public record ConvertPurchaseDocumentRequest(@NotNull PurchaseDocumentType targetType, LocalDate issueDate) {
    }

    public record PurchaseLineResponse(UUID id, int sequence, UUID productId, String productCode,
                                       String description, String unitOfMeasure, BigDecimal quantity,
                                       BigDecimal unitPrice, BigDecimal discountPercentage,
                                       BigDecimal taxPercentage, BigDecimal netAmount) {
        static PurchaseLineResponse from(PurchaseDocumentLine line) {
            return new PurchaseLineResponse(line.getId(), line.getLineSequence(), line.getProductId(),
                    line.getProductCodeSnapshot(), line.getDescription(), line.getUnitOfMeasureSnapshot(),
                    line.getQuantity(), line.getUnitPrice(), line.getDiscountPercentage(),
                    line.getTaxPercentage(), line.getNetAmount());
        }
    }

    public record PurchaseDocumentResponse(UUID id, PurchaseDocumentType type, String number,
                                           PurchaseDocumentStatus status, UUID supplierId, String supplierCode,
                                           String supplierName, String supplierTaxId, String supplierReference,
                                           LocalDate issueDate, LocalDate expectedDate, UUID warehouseId,
                                           String currencyCode, UUID sourceDocumentId,
                                           boolean stockReceivedUpstream, boolean stockPosted,
                                           BigDecimal netAmount, BigDecimal taxAmount, BigDecimal totalAmount,
                                           String notes, List<PurchaseLineResponse> lines, Instant createdAt,
                                           Instant updatedAt) {
        static PurchaseDocumentResponse from(PurchaseDocument document, List<PurchaseDocumentLine> lines) {
            return new PurchaseDocumentResponse(document.getId(), document.getType(), document.getNumber(),
                    document.getStatus(), document.getSupplierId(), document.getSupplierCodeSnapshot(),
                    document.getSupplierNameSnapshot(), document.getSupplierTaxIdSnapshot(),
                    document.getSupplierReference(), document.getIssueDate(), document.getExpectedDate(),
                    document.getWarehouseId(), document.getCurrencyCode(), document.getSourceDocumentId(),
                    document.isStockReceivedUpstream(), document.isStockPosted(), document.getNetAmount(),
                    document.getTaxAmount(), document.getTotalAmount(), document.getNotes(),
                    lines.stream().map(PurchaseLineResponse::from).toList(), document.getCreatedAt(),
                    document.getUpdatedAt());
        }
    }
}
