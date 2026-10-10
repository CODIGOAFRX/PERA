package com.peraerp.operations.salesdelivery;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tickets del TPV de finance-service. Llegan con la misma forma que las entregas de Ventas: un
 * ticket de venta es una salida (POS_TICKET) y una devolución, una entrada (POS_RETURN).
 */
public interface PosTicketDeliveryClient {

    /** Tickets modificados desde el instante dado, o vacío si Finanzas no está disponible. */
    Optional<List<SalesDeliverySnapshot>> findUpdatedSince(UUID companyId, Instant updatedSince);
}
