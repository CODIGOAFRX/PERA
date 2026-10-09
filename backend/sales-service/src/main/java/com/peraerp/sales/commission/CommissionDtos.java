package com.peraerp.sales.commission;

import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.DocumentType;
import com.peraerp.sales.document.PaymentStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Peticiones y respuestas de reglas, cálculo y liquidación de comisiones. */
public final class CommissionDtos {
    private CommissionDtos() {}

    public record RuleRequest(
            @NotNull UUID salespersonId,
            UUID productId,
            UUID productGroupId,
            @DecimalMin("0") BigDecimal amountFrom,
            @DecimalMin("0") BigDecimal amountTo,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal percentage,
            Boolean active
    ) {}

    public record RuleResponse(UUID id, UUID salespersonId, UUID productId, String productLabel, UUID productGroupId,
                               String productGroupLabel, BigDecimal amountFrom, BigDecimal amountTo,
                               BigDecimal percentage, boolean active) {
        static RuleResponse from(CommissionRule rule) {
            return new RuleResponse(rule.getId(), rule.getSalespersonId(), rule.getProductId(), rule.getProductLabel(),
                    rule.getProductGroupId(), rule.getProductGroupLabel(), rule.getAmountFrom(), rule.getAmountTo(),
                    rule.getPercentage(), rule.isActive());
        }
    }

    public record CalculateRequest(UUID salespersonId, @NotNull LocalDate fromDate, @NotNull LocalDate toDate) {}

    public record CalculateResponse(int documents, int calculated, int settledSkipped, BigDecimal commissionAmount) {}

    public record SettleRequest(@NotEmpty List<UUID> ids, @NotNull LocalDate settledOn, @Size(max = 300) String note) {}

    public record SalespersonChange(UUID salespersonId) {}

    public record LineResponse(int order, String description, BigDecimal baseAmount, BigDecimal percentage,
                               BigDecimal commissionAmount, SalesCommissionLine.Origin origin) {
        static LineResponse from(SalesCommissionLine line) {
            return new LineResponse(line.getLineOrder(), line.getDescription(), line.getBaseAmount(),
                    line.getPercentage(), line.getCommissionAmount(), line.getOrigin());
        }
    }

    public record CommissionResponse(UUID id, UUID documentId, String documentNumber, DocumentType documentType,
                                     LocalDate issueDate, String customerName, PaymentStatus paymentStatus,
                                     UUID salespersonId, String salespersonName, BigDecimal baseAmount,
                                     BigDecimal commissionAmount, SalesCommission.Status status, Instant calculatedAt,
                                     LocalDate settledOn, String settlementNote, List<LineResponse> lines) {
        static CommissionResponse from(SalesCommission commission, CommercialDocument document, boolean withLines) {
            return new CommissionResponse(commission.getId(), commission.getDocumentId(),
                    document == null ? null : document.getDocumentNumber(),
                    document == null ? null : document.getType(),
                    document == null ? null : document.getIssueDate(),
                    document == null ? null : document.getCustomerNameSnapshot(),
                    document == null ? null : document.getPaymentStatus(),
                    commission.getSalespersonId(), commission.getSalespersonName(), commission.getBaseAmount(),
                    commission.getCommissionAmount(), commission.getStatus(), commission.getCalculatedAt(),
                    commission.getSettledOn(), commission.getSettlementNote(),
                    withLines ? commission.getLines().stream().map(LineResponse::from).toList() : List.of());
        }
    }

    public record Totals(long count, BigDecimal baseAmount, BigDecimal commissionAmount) {}
}
