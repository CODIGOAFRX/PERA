package com.peraerp.sales.verifactu.chain;

import com.peraerp.sales.verifactu.domain.InvoiceChainHeadRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vigilancia de las dos anotaciones que sostienen la integridad de la cadena.
 *
 * <p>Nadie va a introducir concurrencia por accidente. Lo que sí puede pasar es que alguien borre
 * una anotación al refactorizar, y entonces el sistema sigue compilando, sigue pasando todas las
 * demás pruebas, y empieza a bifurcar cadenas en producción bajo carga. Estas dos comprobaciones
 * son baratas y detectan exactamente esa regresión.</p>
 *
 * <p>No sustituyen a la prueba de concurrencia real contra PostgreSQL: comprueban que la intención
 * sigue declarada, no que funcione.</p>
 */
class VerifactuChainLockingGuardTest {

    @Test
    void chainHeadIsReadWithAPessimisticWriteLock() throws NoSuchMethodException {
        Method lookup = InvoiceChainHeadRepository.class.getMethod("findByCompanyId", UUID.class);

        Lock lock = lookup.getAnnotation(Lock.class);

        assertThat(lock)
                .as("findByCompanyId debe bloquear el puntero de cadena; sin el bloqueo, dos "
                        + "emisiones simultáneas de la misma empresa leerían la misma huella anterior")
                .isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void appendingARecordDemandsAnExistingTransaction() throws NoSuchMethodException {
        Method append = VerifactuChainService.class.getMethod("append", UUID.class, ChainedRecordRequest.class);

        Transactional transactional = append.getAnnotation(Transactional.class);

        assertThat(transactional)
                .as("append debe exigir transacción abierta: encadenar el registro y expedir la "
                        + "factura tienen que ser una sola operación atómica")
                .isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
