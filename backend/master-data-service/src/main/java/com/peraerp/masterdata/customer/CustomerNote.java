package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Nota interna sobre un cliente. Las marcadas «mostrar en documentos» sustituyen al texto para documentos del
 * programa anterior. Al borrarla se desactiva para no perder el histórico.
 */
@Entity
@Table(name = "customer_notes")
public class CustomerNote extends CompanyScopedEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String message;
    @Column(name = "show_on_documents", nullable = false)
    private boolean showOnDocuments;
    @Column(nullable = false)
    private boolean active = true;

    protected CustomerNote() {}

    public CustomerNote(UUID companyId, UUID customerId) {
        super(companyId);
        this.customerId = customerId;
    }

    public void update(String title, String message, boolean showOnDocuments) {
        this.title = title;
        this.message = message;
        this.showOnDocuments = showOnDocuments;
    }

    public void deactivate() { this.active = false; }

    public UUID getCustomerId() { return customerId; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isShowOnDocuments() { return showOnDocuments; }
    public boolean isActive() { return active; }
}
