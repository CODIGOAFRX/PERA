package com.peraerp.identity.access;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
    List<Role> findAllByCompanyIdOrderByName(UUID companyId);
}
