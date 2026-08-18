package com.peraerp.finance.currency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurrencyRepository extends JpaRepository<CurrencyDefinition, UUID> {
    List<CurrencyDefinition> findAllByCompanyIdOrderByCode(UUID companyId);
    Optional<CurrencyDefinition> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<CurrencyDefinition> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    @Modifying
    @Query("update CurrencyDefinition c set c.baseCurrency = false where c.companyId = :companyId " +
            "and c.id <> :excludedId and c.baseCurrency = true")
    int clearBaseCurrency(@Param("companyId") UUID companyId, @Param("excludedId") UUID excludedId);
}
