package com.peraerp.finance.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CashRegisterRepository extends JpaRepository<CashRegister, UUID> {

    Optional<CashRegister> findByIdAndCompanyId(UUID id, UUID companyId);

    List<CashRegister> findAllByCompanyIdOrderByCodeAsc(UUID companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
