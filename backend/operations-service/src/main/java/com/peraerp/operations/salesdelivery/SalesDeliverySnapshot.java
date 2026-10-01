package com.peraerp.operations.salesdelivery;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Entrega de mercancía tal como la publica sales-service en su punto interno. */
public record SalesDeliverySnapshot(UUID id, String number, String type, String status, LocalDate issueDate,
                                    String customerCode, String customerName, Instant updatedAt, List<Line> lines) {

    public record Line(int order, UUID productId, String productCode, String description, BigDecimal quantity) {
    }

    public boolean cancelled() {
        return "CANCELLED".equals(status);
    }
}
