package com.peraerp.masterdata.customer;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

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
}
