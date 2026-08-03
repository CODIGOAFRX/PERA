package com.peraerp.finance.risk;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomerRiskRepository extends JpaRepository<CustomerRisk,UUID>{Optional<CustomerRisk> findByCompanyIdAndCustomerId(UUID companyId,UUID customerId);}
