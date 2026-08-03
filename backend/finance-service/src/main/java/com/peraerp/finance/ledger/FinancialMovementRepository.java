package com.peraerp.finance.ledger;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface FinancialMovementRepository extends JpaRepository<FinancialMovement,UUID>{}
