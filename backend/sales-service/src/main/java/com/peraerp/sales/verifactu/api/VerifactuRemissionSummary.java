package com.peraerp.sales.verifactu.api;

import com.peraerp.sales.verifactu.domain.VerifactuEnvironment;
import com.peraerp.sales.verifactu.domain.VerifactuState;

import java.time.Instant;
import java.util.Map;

/**
 * Situación de la remisión a la AEAT de una empresa, para la pantalla de seguimiento.
 *
 * @param enabled             Veri*Factu activado en la empresa
 * @param connectionConfigured hay certificado guardado
 * @param connectionActive    la conexión está activada y, por tanto, se remite automáticamente
 * @param nextSendAt          a partir de cuándo se puede remitir (tiempo de espera de la AEAT o reintento)
 * @param failures            envíos fallidos seguidos; con cada uno se espacia más el siguiente intento
 * @param lastError           motivo del último envío fallido
 * @param counts              registros de alta por estado
 */
public record VerifactuRemissionSummary(
        boolean enabled,
        VerifactuEnvironment environment,
        boolean connectionConfigured,
        boolean connectionActive,
        Instant nextSendAt,
        Instant lastSentAt,
        int failures,
        String lastError,
        Map<VerifactuState, Long> counts) {
}
