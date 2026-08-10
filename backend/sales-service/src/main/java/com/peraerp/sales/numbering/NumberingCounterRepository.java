package com.peraerp.sales.numbering;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NumberingCounterRepository extends JpaRepository<NumberingCounter, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NumberingCounter> findByCompanyIdAndSchemeIdAndPeriodKey(
            UUID companyId, UUID schemeId, String periodKey);

    @Modifying
    @Query(value = """
            INSERT INTO numbering_counters
                (id, company_id, scheme_id, period_key, next_value, created_at, updated_at, version)
            VALUES (:id, :companyId, :schemeId, :periodKey, :initialValue, :now, :now, 0)
            ON CONFLICT (company_id, scheme_id, period_key) DO NOTHING
            """, nativeQuery = true)
    int ensureCounter(@Param("id") UUID id, @Param("companyId") UUID companyId,
                      @Param("schemeId") UUID schemeId, @Param("periodKey") String periodKey,
                      @Param("initialValue") long initialValue, @Param("now") Instant now);
}
