package com.peraerp.operations.purchasing;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseDocumentSequenceRepository extends JpaRepository<PurchaseDocumentSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PurchaseDocumentSequence s where s.companyId = :companyId and s.type = :type " +
            "and s.fiscalYear = :fiscalYear")
    Optional<PurchaseDocumentSequence> findForUpdate(@Param("companyId") UUID companyId,
                                                     @Param("type") PurchaseDocumentType type,
                                                     @Param("fiscalYear") int fiscalYear);
}
