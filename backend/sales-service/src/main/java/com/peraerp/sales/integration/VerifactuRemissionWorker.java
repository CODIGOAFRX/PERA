package com.peraerp.sales.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * Remite a la AEAT, cada poco, lo pendiente de las empresas que tienen la conexión activa.
 *
 * <p>Se puede apagar con {@code pera.verifactu.remission.enabled=false}, por ejemplo en una instalación
 * de demostración que no deba hablar con la AEAT. El intervalo es {@code pera.verifactu.remission.poll-ms}.</p>
 */
@Component
@ConditionalOnProperty(name = "pera.verifactu.remission.enabled", havingValue = "true", matchIfMissing = true)
public class VerifactuRemissionWorker {
    private static final Logger log = LoggerFactory.getLogger(VerifactuRemissionWorker.class);
    private final VerifactuRemissionService remission;
    private final FiscalProfileService profiles;

    public VerifactuRemissionWorker(VerifactuRemissionService remission, FiscalProfileService profiles) {
        this.remission = remission;
        this.profiles = profiles;
    }

    @Scheduled(fixedDelayString = "${pera.verifactu.remission.poll-ms:30000}",
            initialDelayString = "${pera.verifactu.remission.initial-delay-ms:30000}")
    public void remitDue() {
        // Un factura_e.ini nuevo o cambiado se tiene en cuenta sin reiniciar el servicio.
        Set<UUID> withFile;
        try {
            withFile = profiles.sync();
        } catch (RuntimeException e) {
            log.error("No se pudieron leer los ficheros factura_e.ini", e);
            withFile = Set.of();
        }
        for (UUID companyId : remission.dueCompanies(withFile)) {
            try {
                remission.remit(companyId);
            } catch (RuntimeException e) {
                // Una empresa con la configuración rota no puede parar la remisión de las demás.
                log.error("No se pudo remitir Veri*Factu de la empresa {}", companyId, e);
            }
        }
    }
}
