package com.peraerp.licensing.license;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface LicenseRepository extends JpaRepository<License, UUID> {
    boolean existsByActivationCodeHash(byte[] activationCodeHash);

    Optional<License> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<License> findAllByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from License l where l.id = :id and l.companyId = :companyId")
    Optional<License> findByIdAndCompanyIdForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from License l where l.id = :id")
    Optional<License> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from License l where l.activationCodeHash = :activationCodeHash")
    Optional<License> findByActivationCodeHashForUpdate(@Param("activationCodeHash") byte[] activationCodeHash);
}
