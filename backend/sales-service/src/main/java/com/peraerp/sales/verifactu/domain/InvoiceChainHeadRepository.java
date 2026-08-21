package com.peraerp.sales.verifactu.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceChainHeadRepository extends JpaRepository<InvoiceChainHead, UUID> {

    /**
     * Lee el puntero de cadena bloqueándolo hasta el final de la transacción.
     *
     * <p>Es el punto donde se serializa la emisión de facturas de una empresa. Sin este bloqueo,
     * dos facturas simultáneas leerían la misma huella anterior y la cadena quedaría bifurcada.</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvoiceChainHead> findByCompanyId(UUID companyId);

    /**
     * Crea el puntero si la empresa todavía no tiene ninguno.
     *
     * <p>Insertar con {@code ON CONFLICT DO NOTHING} evita la carrera entre dos primeras facturas
     * simultáneas, que es justo el momento en el que no hay fila que bloquear. Mismo patrón que
     * {@code NumberingCounterRepository.ensureCounter}.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO verifactu_chain_head
                (id, company_id, next_sequence, created_at, updated_at, version)
            VALUES (:id, :companyId, 1, :now, :now, 0)
            ON CONFLICT (company_id) DO NOTHING
            """, nativeQuery = true)
    int ensureHead(@Param("id") UUID id, @Param("companyId") UUID companyId, @Param("now") Instant now);
}
