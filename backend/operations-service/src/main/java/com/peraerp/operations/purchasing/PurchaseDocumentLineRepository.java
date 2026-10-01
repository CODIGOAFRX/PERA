package com.peraerp.operations.purchasing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseDocumentLineRepository extends JpaRepository<PurchaseDocumentLine, UUID> {

    List<PurchaseDocumentLine> findAllByCompanyIdAndDocumentIdOrderByLineSequenceAsc(UUID companyId, UUID documentId);

    void deleteAllByCompanyIdAndDocumentId(UUID companyId, UUID documentId);
}
