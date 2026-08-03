package com.peraerp.masterdata.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CustomerSpecialRateRepository extends JpaRepository<CustomerSpecialRate, UUID> {
    List<CustomerSpecialRate> findAllByCompanyIdAndCustomerIdAndActiveTrue(UUID companyId, UUID customerId);
}
