package com.peraerp.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingInboxRepository extends JpaRepository<AccountingInboxItem, UUID> {
    Optional<AccountingInboxItem> findByCompanyIdAndSourceTypeAndSourceId(UUID companyId, String sourceType, UUID sourceId);
    Optional<AccountingInboxItem> findByIdAndCompanyIdAndInboxStatus(UUID id, UUID companyId, AccountingInboxStatus status);
    List<AccountingInboxItem> findAllByCompanyIdAndInboxStatusOrderBySourceDateDescCreatedAtDesc(UUID companyId, AccountingInboxStatus status);
    long countByCompanyIdAndInboxStatus(UUID companyId, AccountingInboxStatus status);
}
