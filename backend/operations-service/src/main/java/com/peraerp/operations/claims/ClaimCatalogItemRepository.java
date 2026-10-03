package com.peraerp.operations.claims;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimCatalogItemRepository extends JpaRepository<ClaimCatalogItem, UUID> {

    Optional<ClaimCatalogItem> findByIdAndCompanyId(UUID id, UUID companyId);

    List<ClaimCatalogItem> findAllByCompanyIdOrderByKindAscNameAsc(UUID companyId);

    List<ClaimCatalogItem> findAllByCompanyIdAndIdIn(UUID companyId, Collection<UUID> ids);

    boolean existsByCompanyIdAndKindAndNameIgnoreCaseAndIdNot(UUID companyId, ClaimCatalogKind kind, String name,
                                                              UUID id);

    boolean existsByCompanyIdAndKindAndNameIgnoreCase(UUID companyId, ClaimCatalogKind kind, String name);
}
