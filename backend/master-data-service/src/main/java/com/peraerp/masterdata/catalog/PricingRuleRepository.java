package com.peraerp.masterdata.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
    List<PricingRule> findAllByCompanyIdAndPriceListId(UUID companyId, UUID priceListId);
    Optional<PricingRule> findByIdAndCompanyIdAndPriceListId(UUID id, UUID companyId, UUID priceListId);
}
