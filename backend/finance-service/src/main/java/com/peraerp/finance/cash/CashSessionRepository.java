package com.peraerp.finance.cash;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CashSessionRepository extends JpaRepository<CashSession,UUID>{}
