package com.peraerp.masterdata.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Clasificación comercial del cliente heredada del programa anterior: grupo, tipo, comercial, forma de entrega,
 * motivo de baja, móvil y cuenta contable. Si una petición no la envía, se conservan los valores guardados.
 */
public record CustomerClassification(
        UUID groupId,
        UUID typeId,
        UUID salespersonId,
        UUID deliveryMethodId,
        @Schema(description = "Solo se guarda si el cliente está dado de baja.")
        UUID inactiveReasonId,
        @Size(max = 40) String mobile,
        @Size(max = 20) String accountingAccount
) {
    static CustomerClassification from(CustomerProfile profile) {
        return new CustomerClassification(profile.getGroupId(), profile.getTypeId(), profile.getSalespersonId(),
                profile.getDeliveryMethodId(), profile.getInactiveReasonId(), profile.getMobile(),
                profile.getAccountingAccount());
    }
}
