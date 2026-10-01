package com.peraerp.finance.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID> {

    List<CashMovement> findAllByCompanyIdAndCashSessionIdOrderByOccurredAtAscCreatedAtAsc(UUID companyId,
                                                                                         UUID cashSessionId);
}
