package com.peraerp.operations.agenda;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Cita o tarea de la agenda. Sin hora de inicio ocupa todo el día. */
@Entity
@Table(name = "agenda_entries")
public class AgendaEntry extends CompanyScopedEntity {

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;
    @Column(name = "start_time")
    private LocalTime startTime;
    @Column(name = "end_time")
    private LocalTime endTime;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 2000)
    private String details;
    @Column(name = "type_id")
    private UUID typeId;
    @Column(name = "assignee_name", length = 160)
    private String assigneeName;
    @Column(name = "customer_id")
    private UUID customerId;
    @Column(name = "customer_name_snapshot", length = 180)
    private String customerNameSnapshot;
    @Column(name = "contact_id")
    private UUID contactId;
    @Column(length = 300)
    private String location;
    @Column(name = "document_reference", length = 100)
    private String documentReference;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgendaEntryStatus status = AgendaEntryStatus.PENDING;
    @Column(name = "completed_on")
    private LocalDate completedOn;
    @Column(length = 1000)
    private String outcome;
    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;
    @Column(name = "created_by_name", nullable = false, length = 160, updatable = false)
    private String createdByName;

    protected AgendaEntry() {
    }

    public AgendaEntry(UUID companyId, UUID createdByUserId, String createdByName) {
        super(companyId);
        this.createdByUserId = createdByUserId;
        this.createdByName = createdByName;
    }

    public void update(LocalDate date, LocalTime startTime, LocalTime endTime, String title, String details,
                       UUID typeId, String assigneeName, UUID customerId, String customerName, UUID contactId,
                       String location, String documentReference) {
        requirePending();
        if (startTime == null && endTime != null) {
            throw new IllegalStateException("Indica la hora de inicio si pones hora de fin.");
        }
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalStateException("La hora de fin debe ser posterior a la de inicio.");
        }
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.title = title;
        this.details = details;
        this.typeId = typeId;
        this.assigneeName = assigneeName;
        this.customerId = customerId;
        this.customerNameSnapshot = customerId == null ? null : customerName;
        this.contactId = contactId;
        this.location = location;
        this.documentReference = documentReference;
    }

    /** Marca la cita como hecha y anota qué se hizo, como «Realizado» del programa anterior. */
    public void complete(LocalDate completedOn, String outcome) {
        requirePending();
        this.status = AgendaEntryStatus.DONE;
        this.completedOn = completedOn;
        this.outcome = outcome;
    }

    public void cancel(String reason) {
        requirePending();
        this.status = AgendaEntryStatus.CANCELLED;
        this.outcome = reason;
    }

    public void reopen() {
        if (status == AgendaEntryStatus.PENDING) {
            throw new IllegalStateException("La cita ya está pendiente.");
        }
        this.status = AgendaEntryStatus.PENDING;
        this.completedOn = null;
        this.outcome = null;
    }

    private void requirePending() {
        if (status != AgendaEntryStatus.PENDING) {
            throw new IllegalStateException("La cita ya está hecha o anulada. Reábrela para cambiarla.");
        }
    }

    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getTitle() { return title; }
    public String getDetails() { return details; }
    public UUID getTypeId() { return typeId; }
    public String getAssigneeName() { return assigneeName; }
    public UUID getCustomerId() { return customerId; }
    public String getCustomerNameSnapshot() { return customerNameSnapshot; }
    public UUID getContactId() { return contactId; }
    public String getLocation() { return location; }
    public String getDocumentReference() { return documentReference; }
    public AgendaEntryStatus getStatus() { return status; }
    public LocalDate getCompletedOn() { return completedOn; }
    public String getOutcome() { return outcome; }
    public String getCreatedByName() { return createdByName; }
}
