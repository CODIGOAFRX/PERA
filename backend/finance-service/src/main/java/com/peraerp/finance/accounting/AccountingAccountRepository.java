package com.peraerp.finance.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingAccountRepository extends JpaRepository<AccountingAccount, UUID> {
    List<AccountingAccount> findAllByCompanyIdAndActiveTrueOrderByCode(UUID companyId);
    Optional<AccountingAccount> findByIdAndCompanyIdAndActiveTrue(UUID id, UUID companyId);
    Optional<AccountingAccount> findByCompanyIdAndCode(UUID companyId, String code);
    boolean existsByCompanyId(UUID companyId);
}
