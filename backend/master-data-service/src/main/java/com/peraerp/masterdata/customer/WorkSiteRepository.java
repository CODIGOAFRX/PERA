package com.peraerp.masterdata.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkSiteRepository extends JpaRepository<WorkSite, UUID> {
    List<WorkSite> findAllByCompanyIdAndCustomerIdAndActiveTrue(UUID companyId, UUID customerId);
}
