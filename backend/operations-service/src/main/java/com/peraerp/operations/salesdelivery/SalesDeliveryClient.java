package com.peraerp.operations.salesdelivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesDeliveryClient {

    /** Entregas modificadas desde el instante dado, o vacío si Ventas no está disponible. */
    Optional<List<SalesDeliverySnapshot>> findUpdatedSince(UUID companyId, Instant updatedSince);
}
