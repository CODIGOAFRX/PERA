package com.peraerp.operations.logistics;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shipment_lines", uniqueConstraints = @UniqueConstraint(
        name = "uk_shipment_line_sequence", columnNames = {"company_id", "shipment_id", "line_sequence"}))
public class ShipmentLine extends CompanyScopedEntity {

    @Column(name = "shipment_id", nullable = false, updatable = false)
    private UUID shipmentId;
    @Column(name = "line_sequence", nullable = false)
    private int lineSequence;
    @Column(name = "product_id")
    private UUID productId;
    @Column(name = "product_code_snapshot", length = 100)
    private String productCodeSnapshot;
    @Column(name = "product_name_snapshot", nullable = false, length = 300)
    private String productNameSnapshot;
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;
    @Column(name = "unit_of_measure_snapshot", nullable = false, length = 30)
    private String unitOfMeasureSnapshot;
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;
    @Column(name = "source_document_type", length = 80)
    private String sourceDocumentType;
    @Column(name = "source_document_number_snapshot", length = 100)
    private String sourceDocumentNumberSnapshot;

    protected ShipmentLine() {
    }

    public ShipmentLine(UUID companyId, UUID shipmentId, int lineSequence, UUID productId,
                        String productCodeSnapshot, String productNameSnapshot, BigDecimal quantity,
                        String unitOfMeasureSnapshot, UUID sourceDocumentId, String sourceDocumentType,
                        String sourceDocumentNumberSnapshot) {
        super(companyId);
        this.shipmentId = shipmentId;
        this.lineSequence = lineSequence;
        this.productId = productId;
        this.productCodeSnapshot = productCodeSnapshot;
        this.productNameSnapshot = productNameSnapshot;
        this.quantity = quantity;
        this.unitOfMeasureSnapshot = unitOfMeasureSnapshot;
        this.sourceDocumentId = sourceDocumentId;
        this.sourceDocumentType = sourceDocumentType;
        this.sourceDocumentNumberSnapshot = sourceDocumentNumberSnapshot;
    }

    public UUID getShipmentId() { return shipmentId; }
    public int getLineSequence() { return lineSequence; }
    public UUID getProductId() { return productId; }
    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnitOfMeasureSnapshot() { return unitOfMeasureSnapshot; }
    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public String getSourceDocumentType() { return sourceDocumentType; }
    public String getSourceDocumentNumberSnapshot() { return sourceDocumentNumberSnapshot; }
}
