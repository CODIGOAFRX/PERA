package com.peraerp.finance.remittance;

import com.peraerp.finance.receivable.ReceiptDtos.ReceiptResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RemittanceDtos {

    private RemittanceDtos() {
    }

    public record RemittanceRequest(
            @NotBlank @Size(max = 80) String bankAccount,
            LocalDate creationDate,
            @Size(max = 500) String notes,
            @NotEmpty @Size(max = 1000) List<@NotNull UUID> receiptIds
    ) {
    }

    public record RemittanceDateRequest(@NotNull LocalDate date) {
    }

    /**
     * {@code invoiceUpdated} solo se informa al liquidar: falso si alguna factura no se pudo marcar
     * como cobrada en Ventas.
     */
    public record RemittanceResponse(UUID id, String remittanceNumber, String bankAccount, String currencyCode,
                                     LocalDate creationDate, LocalDate sentDate, LocalDate settlementDate,
                                     RemittanceStatus status, BigDecimal totalAmount, String notes,
                                     List<ReceiptResponse> receipts, Boolean invoiceUpdated) {
    }
}
