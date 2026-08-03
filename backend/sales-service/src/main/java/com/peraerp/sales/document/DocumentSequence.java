package com.peraerp.sales.document;

import com.peraerp.platform.domain.CompanyScopedEntity;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "document_sequences", uniqueConstraints = @UniqueConstraint(name = "uk_document_sequence", columnNames = {"company_id", "document_type", "sequence_year"}))
public class DocumentSequence extends CompanyScopedEntity {
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType type;
    @Column(name = "sequence_year", nullable = false)
    private int year;
    @Column(name = "next_value", nullable = false)
    private long nextValue = 1;
    protected DocumentSequence() {}
    public DocumentSequence(UUID companyId, DocumentType type, int year) { super(companyId); this.type=type; this.year=year; }
    public long takeNext() { return nextValue++; }
}
