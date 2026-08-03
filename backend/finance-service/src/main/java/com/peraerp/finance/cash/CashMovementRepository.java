package com.peraerp.finance.cash;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CashMovementRepository extends JpaRepository<CashMovement,UUID>{}
