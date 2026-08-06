package com.peraerp.masterdata.customer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Acceso de compatibilidad al modelo heredado de obras.
 *
 * @deprecated No debe incorporarse a nuevos flujos del núcleo horizontal. Se mantiene para preservar el modelo
 * existente hasta que se defina una extensión genérica de proyectos o ubicaciones de servicio.
 */
@Deprecated(since = "0.2", forRemoval = false)
public interface WorkSiteRepository extends JpaRepository<WorkSite, UUID> {
    List<WorkSite> findAllByCompanyIdAndCustomerIdAndActiveTrue(UUID companyId, UUID customerId);
}
