package com.peraerp.finance.cash;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CashSessionRepository extends JpaRepository<CashSession, UUID> {

    Optional<CashSession> findByIdAndCompanyId(UUID id, UUID companyId);

    /** Bloquea la sesión para que un apunte y el cierre simultáneos se apliquen en orden. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashSession s where s.id = :id and s.companyId = :companyId")
    Optional<CashSession> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Optional<CashSession> findFirstByCompanyIdAndCashRegisterIdAndStatus(UUID companyId, UUID cashRegisterId,
                                                                         CashSessionStatus status);

    boolean existsByCompanyIdAndCashRegisterIdAndStatus(UUID companyId, UUID cashRegisterId, CashSessionStatus status);

    Optional<CashSession> findFirstByCompanyIdAndCashRegisterIdAndStatusOrderByClosedAtDesc(
            UUID companyId, UUID cashRegisterId, CashSessionStatus status);

    /** Sesiones abiertas en un intervalo y las que siguen abiertas, para el seguimiento del TPV. */
    @Query("select s from CashSession s where s.companyId = :companyId and ((s.openedAt >= :from and s.openedAt < :to) "
            + "or s.status = com.peraerp.finance.cash.CashSessionStatus.OPEN) order by s.openedAt asc")
    java.util.List<CashSession> findForPeriod(@Param("companyId") UUID companyId, @Param("from") java.time.Instant from,
                                             @Param("to") java.time.Instant to);

    @Query("select s from CashSession s where s.companyId = :companyId " +
            "and (:filterRegister = false or s.cashRegisterId = :cashRegisterId) " +
            "and (:filterStatus = false or s.status = :status)")
    Page<CashSession> search(@Param("companyId") UUID companyId,
                             @Param("filterRegister") boolean filterRegister,
                             @Param("cashRegisterId") UUID cashRegisterId,
                             @Param("filterStatus") boolean filterStatus, @Param("status") CashSessionStatus status,
                             Pageable pageable);
}
