package com.peraerp.masterdata.catalog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface PriceListItemRepository extends JpaRepository<PriceListItem, UUID> {
    List<PriceListItem> findAllByCompanyIdAndPriceListId(UUID companyId, UUID priceListId);
}
