package com.peraerp.sales.document;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import java.util.UUID;

public interface DocumentSequenceRepository extends JpaRepository<DocumentSequence, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentSequence> findByCompanyIdAndTypeAndYear(UUID companyId, DocumentType type, int year);
}
