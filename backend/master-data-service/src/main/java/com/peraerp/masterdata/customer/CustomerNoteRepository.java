package com.peraerp.masterdata.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerNoteRepository extends JpaRepository<CustomerNote, UUID> {
    List<CustomerNote> findAllByCompanyIdAndCustomerIdAndActiveTrue(UUID companyId, UUID customerId);

    List<CustomerNote> findAllByCompanyIdAndCustomerIdAndActiveTrueOrderByCreatedAtDesc(UUID companyId,
                                                                                       UUID customerId);

    Optional<CustomerNote> findByIdAndCompanyIdAndCustomerIdAndActiveTrue(UUID id, UUID companyId, UUID customerId);
}
