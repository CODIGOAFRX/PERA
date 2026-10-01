package com.peraerp.operations.salesdelivery;

import com.peraerp.operations.inventory.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lee periódicamente las ventas de las empresas que llevan inventario, para que un albarán
 * confirmado descuente existencias aunque nadie abra la pantalla de almacén.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "pera.inventory.sales-sync.enabled", havingValue = "true", matchIfMissing = true)
public class SalesDeliverySyncJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(SalesDeliverySyncJob.class);

    private final WarehouseRepository warehouseRepository;
    private final SalesDeliveryService service;

    public SalesDeliverySyncJob(WarehouseRepository warehouseRepository, SalesDeliveryService service) {
        this.warehouseRepository = warehouseRepository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${pera.inventory.sales-sync.delay:PT1M}",
            initialDelayString = "${pera.inventory.sales-sync.delay:PT1M}")
    public void synchronizeAll() {
        for (UUID companyId : warehouseRepository.findCompanyIdsWithActiveWarehouses()) {
            try {
                service.synchronize(companyId);
            } catch (RuntimeException exception) {
                // Una empresa con un problema no impide sincronizar las demás.
                LOGGER.warn("Fallo al sincronizar las salidas de venta de la empresa {}: {}", companyId,
                        exception.getClass().getSimpleName());
            }
        }
    }
}
