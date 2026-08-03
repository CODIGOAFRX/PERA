package com.peraerp.finance.remittance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface RemittanceRepository extends JpaRepository<Remittance,UUID>{}
