package com.peraerp.operations.salesdelivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface PosTicketSyncRepository extends JpaRepository<PosTicketSync, UUID> {

    /** El bloqueo pone en fila la lectura periódica y el aviso inmediato del TPV de la misma empresa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PosTicketSync> findByCompanyId(UUID companyId);
}
