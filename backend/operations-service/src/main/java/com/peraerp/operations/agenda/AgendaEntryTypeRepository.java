package com.peraerp.operations.agenda;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaEntryTypeRepository extends JpaRepository<AgendaEntryType, UUID> {

    Optional<AgendaEntryType> findByIdAndCompanyId(UUID id, UUID companyId);

    List<AgendaEntryType> findAllByCompanyIdOrderByNameAsc(UUID companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);

    boolean existsByCompanyIdAndNameIgnoreCaseAndIdNot(UUID companyId, String name, UUID id);
}
