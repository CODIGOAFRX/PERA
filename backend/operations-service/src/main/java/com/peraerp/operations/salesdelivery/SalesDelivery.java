package com.peraerp.operations.salesdelivery;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Albarán o factura de venta que debe dar salida de almacén, con el estado de esa salida. */
@Entity
@Table(name = "sales_deliveries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sales_delivery_source", columnNames = {"company_id", "source_document_id"}),
        @UniqueConstraint(name = "uk_sales_delivery_company_id", columnNames = {"company_id", "id"})
})
public class SalesDelivery extends CompanyScopedEntity {

    private static final int PROBLEM_LENGTH = 500;

    @Column(name = "source_document_id", nullable = false, updatable = false)
    private UUID sourceDocumentId;
    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;
    @Column(name = "source_number", nullable = false, length = 100)
    private String sourceNumber;
    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;
    @Column(name = "source_status", nullable = false, length = 30)
    private String sourceStatus;
    @Column(name = "customer_code", length = 60)
    private String customerCode;
    @Column(name = "customer_name", nullable = false, length = 180)
    private String customerName;
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private SalesDeliveryStatus status = SalesDeliveryStatus.PENDING;
    @Column(name = "warehouse_id")
    private UUID warehouseId;
    @Column(length = PROBLEM_LENGTH)
    private String problem;
    @Column(name = "posted_at")
    private Instant postedAt;

    protected SalesDelivery() {
    }

    public SalesDelivery(UUID companyId, SalesDeliverySnapshot snapshot) {
        super(companyId);
        this.sourceDocumentId = snapshot.id();
        refresh(snapshot);
    }

    public void refresh(SalesDeliverySnapshot snapshot) {
        this.sourceType = snapshot.type();
        this.sourceNumber = snapshot.number();
        this.sourceDate = snapshot.issueDate();
        this.sourceStatus = snapshot.status();
        this.customerCode = snapshot.customerCode();
        this.customerName = snapshot.customerName();
    }

    public void markPosted(UUID warehouseId, Instant postedAt) {
        requirePending();
        this.status = SalesDeliveryStatus.POSTED;
        this.warehouseId = warehouseId;
        this.postedAt = postedAt;
        this.problem = null;
    }

    public void markNotApplicable() {
        requirePending();
        this.status = SalesDeliveryStatus.NOT_APPLICABLE;
        this.problem = null;
    }

    public void markReversed() {
        if (status != SalesDeliveryStatus.POSTED) {
            throw new IllegalStateException("Solo se puede devolver al almacén una salida ya anotada.");
        }
        this.status = SalesDeliveryStatus.REVERSED;
    }

    public void dismiss() {
        requirePending();
        this.status = SalesDeliveryStatus.DISMISSED;
    }

    public void reportProblem(String problem) {
        requirePending();
        this.problem = problem.length() <= PROBLEM_LENGTH ? problem : problem.substring(0, PROBLEM_LENGTH - 3) + "...";
    }

    private void requirePending() {
        if (status != SalesDeliveryStatus.PENDING) {
            throw new IllegalStateException("La salida ya no está pendiente.");
        }
    }

    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public String getSourceType() { return sourceType; }
    public String getSourceNumber() { return sourceNumber; }
    public LocalDate getSourceDate() { return sourceDate; }
    public String getSourceStatus() { return sourceStatus; }
    public String getCustomerCode() { return customerCode; }
    public String getCustomerName() { return customerName; }
    public SalesDeliveryStatus getStatus() { return status; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getProblem() { return problem; }
    public Instant getPostedAt() { return postedAt; }
}
