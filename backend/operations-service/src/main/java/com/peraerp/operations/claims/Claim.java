package com.peraerp.operations.claims;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Reclamación de un cliente: qué ha fallado, por qué, quién responde y cómo se ha resuelto. */
@Entity
@Table(name = "claims", uniqueConstraints = {
        @UniqueConstraint(name = "uk_claim_company_number", columnNames = {"company_id", "claim_number"}),
        @UniqueConstraint(name = "uk_claim_company_id", columnNames = {"company_id", "id"})
})
public class Claim extends CompanyScopedEntity {

    @Column(name = "claim_number", nullable = false, length = 40, updatable = false)
    private String number;
    @Column(name = "claim_date", nullable = false)
    private LocalDate claimDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.OPEN;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(name = "customer_code_snapshot", length = 60)
    private String customerCodeSnapshot;
    @Column(name = "customer_name_snapshot", nullable = false, length = 180)
    private String customerNameSnapshot;
    @Column(name = "source_document_id")
    private UUID sourceDocumentId;
    @Column(name = "source_document_number", length = 100)
    private String sourceDocumentNumber;
    @Column(name = "source_document_date")
    private LocalDate sourceDocumentDate;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(name = "reported_by_user_id", nullable = false, updatable = false)
    private UUID reportedByUserId;
    @Column(name = "reported_by_name", nullable = false, length = 160, updatable = false)
    private String reportedByName;
    @Column(name = "reason_id")
    private UUID reasonId;
    @Column(name = "nonconformity_id")
    private UUID nonconformityId;
    @Column(name = "cause_id")
    private UUID causeId;
    @Column(name = "area_id")
    private UUID areaId;
    @Column(name = "responsible_id")
    private UUID responsibleId;
    @Column(name = "resolution_id")
    private UUID resolutionId;
    @Column(name = "preventive_action_id")
    private UUID preventiveActionId;
    @Column(name = "follow_up_date")
    private LocalDate followUpDate;
    @Column(name = "closed_on")
    private LocalDate closedOn;
    @Column(name = "closing_note", length = 1000)
    private String closingNote;

    protected Claim() {
    }

    public Claim(UUID companyId, String number, UUID reportedByUserId, String reportedByName) {
        super(companyId);
        this.number = number;
        this.reportedByUserId = reportedByUserId;
        this.reportedByName = reportedByName;
    }

    public void update(LocalDate claimDate, UUID customerId, String customerCode, String customerName,
                       UUID sourceDocumentId, String sourceDocumentNumber, LocalDate sourceDocumentDate,
                       String description, Map<ClaimCatalogKind, UUID> classification, LocalDate followUpDate) {
        requireOpen();
        this.claimDate = claimDate;
        this.customerId = customerId;
        this.customerCodeSnapshot = customerCode;
        this.customerNameSnapshot = customerName;
        this.sourceDocumentId = sourceDocumentId;
        this.sourceDocumentNumber = sourceDocumentNumber;
        this.sourceDocumentDate = sourceDocumentDate;
        this.description = description;
        this.reasonId = classification.get(ClaimCatalogKind.REASON);
        this.nonconformityId = classification.get(ClaimCatalogKind.NONCONFORMITY);
        this.causeId = classification.get(ClaimCatalogKind.CAUSE);
        this.areaId = classification.get(ClaimCatalogKind.AREA);
        this.responsibleId = classification.get(ClaimCatalogKind.RESPONSIBLE);
        this.resolutionId = classification.get(ClaimCatalogKind.RESOLUTION);
        this.preventiveActionId = classification.get(ClaimCatalogKind.PREVENTIVE_ACTION);
        this.followUpDate = followUpDate;
    }

    /** Cierra la reclamación. Sin resolución no se puede cerrar: quedaría sin saber cómo acabó. */
    public void close(LocalDate closedOn, String closingNote) {
        requireOpen();
        if (resolutionId == null) {
            throw new IllegalStateException("Indica la resolución antes de cerrar la reclamación.");
        }
        if (closedOn.isBefore(claimDate)) {
            throw new IllegalStateException("La fecha de cierre no puede ser anterior a la de la reclamación.");
        }
        this.status = ClaimStatus.CLOSED;
        this.closedOn = closedOn;
        this.closingNote = closingNote;
    }

    public void reopen() {
        if (status != ClaimStatus.CLOSED) {
            throw new IllegalStateException("La reclamación ya está abierta.");
        }
        this.status = ClaimStatus.OPEN;
        this.closedOn = null;
        this.closingNote = null;
    }

    public void requireOpen() {
        if (status != ClaimStatus.OPEN) {
            throw new IllegalStateException("La reclamación está cerrada. Reábrela para modificarla.");
        }
    }

    public UUID classification(ClaimCatalogKind kind) {
        return switch (kind) {
            case REASON -> reasonId;
            case NONCONFORMITY -> nonconformityId;
            case CAUSE -> causeId;
            case AREA -> areaId;
            case RESPONSIBLE -> responsibleId;
            case RESOLUTION -> resolutionId;
            case PREVENTIVE_ACTION -> preventiveActionId;
        };
    }

    public String getNumber() { return number; }
    public LocalDate getClaimDate() { return claimDate; }
    public ClaimStatus getStatus() { return status; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerCodeSnapshot() { return customerCodeSnapshot; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot; }
    public UUID getSourceDocumentId() { return sourceDocumentId; }
    public String getSourceDocumentNumber() { return sourceDocumentNumber; }
    public LocalDate getSourceDocumentDate() { return sourceDocumentDate; }
    public String getDescription() { return description; }
    public UUID getReportedByUserId() { return reportedByUserId; }
    public String getReportedByName() { return reportedByName; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public LocalDate getClosedOn() { return closedOn; }
    public String getClosingNote() { return closingNote; }
}
