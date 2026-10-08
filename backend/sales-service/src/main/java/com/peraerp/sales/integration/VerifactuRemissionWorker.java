package com.peraerp.sales.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    public VerifactuRemissionWorker(VerifactuRemissionService remission) {
        this.remission = remission;
    }

    @Scheduled(fixedDelayString = "${pera.verifactu.remission.poll-ms:30000}",
            initialDelayString = "${pera.verifactu.remission.initial-delay-ms:30000}")
    public void remitDue() {
        for (UUID companyId : remission.dueCompanies()) {
            try {
                remission.remit(companyId);
            } catch (RuntimeException e) {
                // Una empresa con la configuración rota no puede parar la remisión de las demás.
                log.error("No se pudo remitir Veri*Factu de la empresa {}", companyId, e);
            }
        }
    }
}
