package com.peraerp.masterdata.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerCatalogItemRepository extends JpaRepository<CustomerCatalogItem, UUID> {
    Optional<CustomerCatalogItem> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndKindAndNameIgnoreCase(UUID companyId, CustomerCatalogKind kind, String name);

    boolean existsByCompanyIdAndKindAndNameIgnoreCaseAndIdNot(UUID companyId, CustomerCatalogKind kind, String name,
                                                              UUID id);

    @Query("select i from CustomerCatalogItem i where i.companyId = :companyId " +
            "and (:kind is null or i.kind = :kind) and (:active is null or i.active = :active) " +
            "order by i.kind asc, lower(i.name) asc")
    List<CustomerCatalogItem> search(@Param("companyId") UUID companyId, @Param("kind") CustomerCatalogKind kind,
                                     @Param("active") Boolean active);
}
