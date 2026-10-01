package com.peraerp.finance.cash;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CashDtos {

    private CashDtos() {
    }

    public record CashRegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$") String code,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 160) String ownerName,
            Boolean active
    ) {
    }

    public record CashRegisterResponse(UUID id, String code, String name, String ownerName, boolean active,
                                       UUID openSessionId) {
    }

    public record OpenCashSessionRequest(
            @NotNull UUID cashRegisterId,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal openingAmount
    ) {
    }

    public record CashMovementRequest(
            @NotNull CashMovementType type,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 300) String concept
    ) {
    }

    public record CloseCashSessionRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal countedAmount,
            @Size(max = 300) String note
    ) {
    }

    public record CashMovementResponse(UUID id, Instant occurredAt, CashMovementType type, BigDecimal amount,
                                       BigDecimal signedAmount, UUID receiptId, String concept) {
        static CashMovementResponse from(CashMovement movement) {
            return new CashMovementResponse(movement.getId(), movement.getOccurredAt(), movement.getType(),
                    movement.getAmount(), movement.signedAmount(), movement.getReceiptId(), movement.getConcept());
        }
    }

    /**
     * {@code balance} es el efectivo que debería haber ahora en la caja. Tras el cierre,
     * {@code difference} es lo contado menos lo esperado.
     */
    public record CashSessionResponse(UUID id, UUID cashRegisterId, CashSessionStatus status, Instant openedAt,
                                      Instant closedAt, BigDecimal openingAmount, BigDecimal balance,
                                      BigDecimal expectedClosingAmount, BigDecimal actualClosingAmount,
                                      BigDecimal difference, String closingNote,
                                      List<CashMovementResponse> movements) {
    }
}
