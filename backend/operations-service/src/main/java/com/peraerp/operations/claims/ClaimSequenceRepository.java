package com.peraerp.operations.claims;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ClaimSequenceRepository extends JpaRepository<ClaimSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClaimSequence s where s.companyId = :companyId and s.fiscalYear = :fiscalYear")
    Optional<ClaimSequence> findForUpdate(@Param("companyId") UUID companyId, @Param("fiscalYear") int fiscalYear);
}
