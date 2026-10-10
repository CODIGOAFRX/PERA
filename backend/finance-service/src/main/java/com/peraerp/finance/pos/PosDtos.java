package com.peraerp.finance.pos;

import com.peraerp.finance.cash.CashDtos.CashMovementResponse;
import com.peraerp.finance.pos.PosCalculator.TaxLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PosDtos {

    private PosDtos() {
    }

    public record OpenPosSessionRequest(
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal openingAmount
    ) {
    }

    /**
     * Línea que se vende. Sin precio se cobra el PVP del catálogo; con precio, es un precio manual
     * del cajero y queda marcado en el ticket.
     */
    public record SaleLineRequest(
            @NotNull UUID productId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent
    ) {
    }

    public record SaleRequest(
            @NotNull UUID sessionId,
            @NotEmpty @Size(max = 300) List<@Valid SaleLineRequest> lines,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            @NotNull PosPaymentMethod paymentMethod,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal cashTendered,
            @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal cardAmount,
            @Size(max = 180) String customerName,
            @Size(max = 30) String customerTaxId,
            @Size(max = 300) String note,
            UUID clientReference
    ) {
    }

    public record VoidTicketRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record RefundLineRequest(
            @NotNull UUID lineId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 3) BigDecimal quantity
    ) {
    }

    public record RefundRequest(
            @NotNull UUID sessionId,
            @NotEmpty @Size(max = 300) List<@Valid RefundLineRequest> lines,
            @NotNull PosPaymentMethod paymentMethod,
            @NotBlank @Size(max = 300) String reason,
            UUID clientReference
    ) {
    }

    public record TicketLineResponse(UUID id, int lineNumber, UUID productId, String productCode, String productName,
                                     String barcode, String unitOfMeasure, BigDecimal quantity, BigDecimal unitPrice,
                                     boolean priceOverridden, BigDecimal discountPercent, BigDecimal taxRate,
                                     BigDecimal lineTotal, BigDecimal refundedQuantity,
                                     BigDecimal refundableQuantity) {
        static TicketLineResponse from(PosTicketLine line) {
            return new TicketLineResponse(line.getId(), line.getLineNumber(), line.getProductId(),
                    line.getProductCode(), line.getProductName(), line.getBarcode(), line.getUnitOfMeasure(),
                    line.getQuantity(), line.getUnitPrice(), line.isPriceOverridden(), line.getDiscountPercent(),
                    line.getTaxRate(), line.getLineTotal(), line.getRefundedQuantity(), line.refundableQuantity());
        }
    }

    public record TicketResponse(UUID id, String number, PosTicketKind kind, PosTicketStatus status, Instant issuedAt,
                                 UUID registerId, String registerCode, String registerName, UUID sessionId,
                                 String cashierName, String customerName, String customerTaxId,
                                 UUID originalTicketId, String originalTicketNumber, String currencyCode,
                                 BigDecimal discountPercent, BigDecimal grossAmount, BigDecimal discountAmount,
                                 BigDecimal totalAmount, BigDecimal taxBaseAmount, BigDecimal taxAmount,
                                 List<TaxLine> taxes, PosPaymentMethod paymentMethod, BigDecimal cashAmount,
                                 BigDecimal cardAmount, BigDecimal cashTendered, BigDecimal changeAmount, String note,
                                 Instant voidedAt, String voidReason, boolean voidable, boolean refundable,
                                 List<TicketLineResponse> lines) {
    }

    /** Sesión abierta de una caja, con lo justo para elegir caja al entrar en el TPV. */
    public record OpenSessionHeader(UUID id, Instant openedAt, BigDecimal openingAmount, BigDecimal cashBalance,
                                    BigDecimal salesTotal, long ticketCount) {
    }

    public record PosRegisterResponse(UUID id, String code, String name, String ownerName, boolean active,
                                      OpenSessionHeader openSession, Instant lastClosedAt) {
    }

    public record ProductSales(UUID productId, String productCode, String productName, BigDecimal quantity,
                               BigDecimal total) {
    }

    public record HourSales(int hour, BigDecimal total, long tickets) {
    }

    public record DaySales(LocalDate date, BigDecimal total, BigDecimal refunds, long tickets) {
    }

    public record GroupSales(String key, String label, BigDecimal total, BigDecimal refunds, long tickets) {
    }

    /** Cifras de un conjunto de tickets: una sesión de caja o un periodo. */
    public record PosFigures(BigDecimal salesTotal, BigDecimal refundsTotal, BigDecimal netTotal,
                             BigDecimal cashSales, BigDecimal cardSales, BigDecimal cashRefunds,
                             BigDecimal cardRefunds, BigDecimal discountTotal, long ticketCount, long refundCount,
                             long voidedCount, BigDecimal averageTicket, BigDecimal itemsSold, List<TaxLine> taxes,
                             List<ProductSales> topProducts, List<HourSales> hourly) {
    }

    /**
     * Resumen de una sesión de caja (informe X mientras está abierta, informe Z al cerrarla).
     * {@code expectedCash} es el efectivo que debe haber en el cajón.
     */
    public record PosSessionSummary(UUID sessionId, UUID registerId, String registerCode, String registerName,
                                    String status, Instant openedAt, Instant closedAt, BigDecimal openingAmount,
                                    BigDecimal manualIncome, BigDecimal manualOutflow, BigDecimal otherCollections,
                                    BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference,
                                    String closingNote, PosFigures figures, List<CashMovementResponse> movements) {
    }

    public record SessionLine(UUID sessionId, String registerCode, String registerName, String status,
                              Instant openedAt, Instant closedAt, BigDecimal netTotal, long ticketCount,
                              BigDecimal difference) {
    }

    public record PosStatsResponse(LocalDate from, LocalDate to, PosFigures figures, List<DaySales> byDay,
                                   List<GroupSales> byRegister, List<GroupSales> byCashier,
                                   List<SessionLine> sessions) {
    }
}
