package com.peraerp.sales.inventory;

import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.DocumentLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Documento de venta que entrega mercancía, tal como lo necesita el almacén para dar la salida. */
public record DeliverySnapshot(UUID id, String number, String type, String status, LocalDate issueDate,
                               String customerCode, String customerName, Instant updatedAt, List<Line> lines) {

    public record Line(int order, UUID productId, String productCode, String description, BigDecimal quantity) {
        static Line from(DocumentLine line) {
            // La cantidad pedida es la que sale físicamente; la facturada puede ser mayor por mínimos de tarifa.
            return new Line(line.getLineOrder(), line.getProductId(), line.getProductCodeSnapshot(),
                    line.getDescription(), line.getRequestedQuantity());
        }
    }

    static DeliverySnapshot from(CommercialDocument document) {
        // Las líneas sin producto (portes, servicios) no tienen existencias que descontar.
        return new DeliverySnapshot(document.getId(), document.getDocumentNumber(), document.getType().name(),
                document.getStatus().name(), document.getIssueDate(), document.getCustomerCodeSnapshot(),
                document.getCustomerNameSnapshot(), document.getUpdatedAt(),
                document.getLines().stream().filter(line -> line.getProductId() != null).map(Line::from).toList());
    }
}
