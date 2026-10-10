package com.peraerp.finance.pos;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PosTicketSequenceRepository extends JpaRepository<PosTicketSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PosTicketSequence s where s.companyId = :companyId and s.cashRegisterId = :registerId "
            + "and s.kind = :kind and s.fiscalYear = :year")
    Optional<PosTicketSequence> findForUpdate(@Param("companyId") UUID companyId, @Param("registerId") UUID registerId,
                                              @Param("kind") PosTicketKind kind, @Param("year") int year);
}
