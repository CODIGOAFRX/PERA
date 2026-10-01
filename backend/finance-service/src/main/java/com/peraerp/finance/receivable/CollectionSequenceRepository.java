package com.peraerp.finance.receivable;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CollectionSequenceRepository extends JpaRepository<CollectionSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CollectionSequence s where s.companyId = :companyId and s.name = :name " +
            "and s.fiscalYear = :fiscalYear")
    Optional<CollectionSequence> findForUpdate(@Param("companyId") UUID companyId, @Param("name") String name,
                                               @Param("fiscalYear") int fiscalYear);
}
