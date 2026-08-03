package com.peraerp.identity.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);
}
