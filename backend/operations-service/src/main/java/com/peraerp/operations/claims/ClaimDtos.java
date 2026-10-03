package com.peraerp.operations.claims;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record CatalogItemRequest(
            @NotNull ClaimCatalogKind kind,
            @NotBlank @Size(max = 200) String name,
            @Min(0) @Max(3650) Integer followUpDays,
            Boolean active
    ) {
    }

    public record CatalogItemResponse(UUID id, ClaimCatalogKind kind, String name, Integer followUpDays,
                                      boolean active) {
        static CatalogItemResponse from(ClaimCatalogItem item) {
            return new CatalogItemResponse(item.getId(), item.getKind(), item.getName(), item.getFollowUpDays(),
                    item.isActive());
        }
    }

    public record ClaimLineRequest(
            UUID productId,
            @Size(max = 100) String productCode,
            @NotBlank @Size(max = 300) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal quantity
    ) {
    }

    /** Las clasificaciones son opcionales al abrir la reclamación: muchas se conocen después. */
    public record ClaimRequest(
            @NotNull LocalDate claimDate,
            @NotNull UUID customerId,
            @Size(max = 60) String customerCode,
            @NotBlank @Size(max = 180) String customerName,
            UUID sourceDocumentId,
            @Size(max = 100) String sourceDocumentNumber,
            LocalDate sourceDocumentDate,
            @NotBlank @Size(max = 2000) String description,
            UUID reasonId,
            UUID nonconformityId,
            UUID causeId,
            UUID areaId,
            UUID responsibleId,
            UUID resolutionId,
            UUID preventiveActionId,
            LocalDate followUpDate,
            @Size(max = 200) List<@Valid @NotNull ClaimLineRequest> lines
    ) {
    }

    public record CloseClaimRequest(@NotNull LocalDate closedOn, @Size(max = 1000) String closingNote) {
    }

    public record CommentRequest(@NotBlank @Size(max = 2000) String text) {
    }

    public record ClaimLineResponse(int sequence, UUID productId, String productCode, String description,
                                    BigDecimal quantity) {
        static ClaimLineResponse from(ClaimLine line) {
            return new ClaimLineResponse(line.getLineSequence(), line.getProductId(), line.getProductCodeSnapshot(),
                    line.getDescription(), line.getQuantity());
        }
    }

    public record CommentResponse(UUID id, String authorName, String text, Instant createdAt) {
        static CommentResponse from(ClaimComment comment) {
            return new CommentResponse(comment.getId(), comment.getAuthorName(), comment.getText(),
                    comment.getCreatedAt());
        }
    }

    /** {@code daysOpen} cuenta hasta el cierre o, si sigue abierta, hasta hoy. */
    public record ClaimResponse(UUID id, String number, LocalDate claimDate, ClaimStatus status, UUID customerId,
                                String customerCode, String customerName, UUID sourceDocumentId,
                                String sourceDocumentNumber, LocalDate sourceDocumentDate, String description,
                                String reportedByName, UUID reasonId, UUID nonconformityId, UUID causeId,
                                UUID areaId, UUID responsibleId, UUID resolutionId, UUID preventiveActionId,
                                LocalDate followUpDate, boolean overdue, LocalDate closedOn, String closingNote,
                                long daysOpen, List<ClaimLineResponse> lines, List<CommentResponse> comments) {
    }
}
