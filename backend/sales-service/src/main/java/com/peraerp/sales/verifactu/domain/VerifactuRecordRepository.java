package com.peraerp.sales.verifactu.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerifactuRecordRepository extends JpaRepository<VerifactuRecord, UUID> {

    List<VerifactuRecord> findByCompanyIdAndDocumentIdOrderBySequenceNumberAsc(UUID companyId, UUID documentId);

    Optional<VerifactuRecord> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<VerifactuRecord> findByCompanyIdAndStateInOrderBySequenceNumberAsc(
            UUID companyId, List<VerifactuState> states, Pageable pageable);

    boolean existsByCompanyIdAndDocumentIdAndRecordType(UUID companyId, UUID documentId, VerifactuRecordType recordType);

    @Query("select distinct r.documentId from VerifactuRecord r where r.companyId = :companyId " +
            "and r.recordType = com.peraerp.sales.verifactu.domain.VerifactuRecordType.ALTA " +
            "and r.documentId in :documentIds")
    List<UUID> findDocumentIdsWithRegistration(@Param("companyId") UUID companyId,
                                                @Param("documentIds") List<UUID> documentIds);
}
