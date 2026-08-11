package com.peraerp.identity.access;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserCompanyRepository extends JpaRepository<UserCompany, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<UserCompany> findAllByUserIdAndActiveTrue(UUID userId);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<UserCompany> findByUserIdAndCompanyIdAndActiveTrue(UUID userId, UUID companyId);

    @EntityGraph(attributePaths = {"user", "roles", "roles.permissions"})
    Optional<UserCompany> findByUserIdAndCompanyId(UUID userId, UUID companyId);

    @EntityGraph(attributePaths = {"user", "roles", "roles.permissions"})
    List<UserCompany> findAllByCompanyIdOrderByCreatedAtAsc(UUID companyId);
}
