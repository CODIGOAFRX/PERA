package com.peraerp.operations.salesdelivery;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface SalesDeliverySyncRepository extends JpaRepository<SalesDeliverySync, UUID> {

    /** El bloqueo pone en fila dos sincronizaciones simultáneas de la misma empresa. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SalesDeliverySync> findByCompanyId(UUID companyId);
}
