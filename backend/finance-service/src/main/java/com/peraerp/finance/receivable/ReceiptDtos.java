package com.peraerp.finance.receivable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReceiptDtos {

    private ReceiptDtos() {
    }

    /** Emite un recibo por cada vencimiento de la factura que aún no lo tenga. */
    public record IssueReceiptsRequest(
            @NotNull UUID documentId,
            @NotBlank @Size(max = 80) String documentNumber,
            @NotNull UUID customerId,
            @Size(max = 60) String customerCode,
            @NotBlank @Size(max = 180) String customerName,
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currencyCode
    ) {
    }

    public record CollectReceiptRequest(
            @NotNull LocalDate collectionDate,
            @NotNull CollectionMethod method,
            UUID cashSessionId,
            @Size(max = 500) String notes
    ) {
    }

    public record ReturnReceiptRequest(@NotNull LocalDate returnDate, @NotBlank @Size(max = 300) String reason) {
    }

    public record ReopenReceiptRequest(LocalDate newDueDate) {
    }

    public record ReceiptResponse(UUID id, String receiptNumber, UUID customerId, String customerCode,
                                  String customerName, UUID documentId, String documentNumber, int installment,
                                  BigDecimal amount, String currencyCode, LocalDate dueDate, ReceiptStatus status,
                                  LocalDate collectionDate, CollectionMethod collectionMethod, LocalDate returnDate,
                                  String returnReason, UUID remittanceId, String notes) {
        public static ReceiptResponse from(Receipt receipt) {
            return new ReceiptResponse(receipt.getId(), receipt.getReceiptNumber(), receipt.getCustomerId(),
                    receipt.getCustomerCodeSnapshot(), receipt.getCustomerNameSnapshot(), receipt.getDocumentId(),
                    receipt.getDocumentNumberSnapshot(), receipt.getInstallmentNumber(), receipt.getAmount(),
                    receipt.getCurrencyCode(), receipt.getDueDate(), receipt.getStatus(),
                    receipt.getCollectionDate(), receipt.getCollectionMethod(), receipt.getReturnDate(),
                    receipt.getReturnReason(), receipt.getRemittanceId(), receipt.getNotes());
        }
    }

    /**
     * Resultado de una operación sobre recibos. {@code invoiceUpdated} es falso cuando no se pudo
     * trasladar a Ventas el nuevo estado de cobro de la factura; la cartera sí queda actualizada.
     */
    public record ReceiptOperationResponse(List<ReceiptResponse> receipts, boolean invoiceUpdated) {
    }
}
