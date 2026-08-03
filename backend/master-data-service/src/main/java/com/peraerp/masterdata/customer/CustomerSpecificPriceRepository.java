package com.peraerp.masterdata.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CustomerSpecificPriceRepository extends JpaRepository<CustomerSpecificPrice, UUID> {
    List<CustomerSpecificPrice> findAllByCompanyIdAndCustomerIdAndActiveTrue(UUID companyId, UUID customerId);
}
