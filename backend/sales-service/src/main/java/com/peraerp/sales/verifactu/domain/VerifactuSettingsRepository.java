package com.peraerp.sales.verifactu.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerifactuSettingsRepository extends JpaRepository<VerifactuSettings, UUID> {

    Optional<VerifactuSettings> findByCompanyId(UUID companyId);

    /**
     * Cuántos obligados tributarios está sirviendo esta instalación.
     *
     * <p>Alimenta {@code IndicadorMultiplesOT}, que la AEAT define como un hecho del momento en que
     * se genera el registro, no como una capacidad del programa.</p>
     */
    long countByEnabledTrue();
}
