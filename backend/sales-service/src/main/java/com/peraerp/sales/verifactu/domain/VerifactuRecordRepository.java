package com.peraerp.sales.verifactu.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerifactuRecordRepository extends JpaRepository<VerifactuRecord, UUID> {

    List<VerifactuRecord> findByCompanyIdAndDocumentIdOrderBySequenceNumberAsc(UUID companyId, UUID documentId);

    Optional<VerifactuRecord> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<VerifactuRecord> findByCompanyIdAndStateInOrderBySequenceNumberAsc(
            UUID companyId, List<VerifactuState> states, Pageable pageable);

    boolean existsByCompanyIdAndDocumentIdAndRecordType(UUID companyId, UUID documentId, VerifactuRecordType recordType);
}
