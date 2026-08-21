package com.peraerp.sales.verifactu.chain;

import com.peraerp.sales.document.CommercialDocument;
import com.peraerp.sales.document.CommercialDocumentRepository;
import com.peraerp.sales.document.DocumentType;
import com.peraerp.sales.verifactu.domain.InvoiceChainHeadRepository;
import com.peraerp.sales.verifactu.domain.InvoiceKind;
import com.peraerp.sales.verifactu.domain.VerifactuRecord;
import com.peraerp.sales.verifactu.domain.VerifactuRecordRepository;
import com.peraerp.sales.verifactu.domain.VerifactuRecordType;
import com.peraerp.sales.verifactu.hash.FingerprintInput;
import com.peraerp.sales.verifactu.hash.RecordFingerprint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia real de la cadena, contra el PostgreSQL local.
 *
 * <p>Las pruebas unitarias del encadenado usan repositorios en memoria: comprueban la lógica, no el
 * bloqueo. Un {@code SELECT ... FOR UPDATE} solo se puede ejercitar con una base de datos de
 * verdad y varias transacciones a la vez, y es justo ahí donde una cadena se bifurca sin que nadie
 * se entere hasta que la AEAT rechaza un registro.</p>
 *
 * <p>La prueba se salta sola si no encuentra el clúster local que levanta
 * {@code scripts/start-local.ps1}. No exige Docker: en el equipo de desarrollo la virtualización
 * está deshabilitada y Testcontainers no arrancaría.</p>
 *
 * <p>Trabaja sobre una empresa con identificador aleatorio, así que no interfiere con los datos de
 * desarrollo aunque comparta base.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${PERA_TEST_DB_URL:jdbc:postgresql://localhost:55432/pera_sales}",
        "spring.datasource.username=${PERA_TEST_DB_USER:pera}",
        "spring.datasource.password=${PERA_TEST_DB_PASSWORD:pera_dev_password}",
        "pera.jwt.secret=pera-local-development-secret-2026-minimum-32-bytes",
        "pera.verifactu.software.developer-tax-id=89890001K",
})
@EnabledIf("localPostgresIsAvailable")
class VerifactuChainConcurrencyTest {

    private static final String URL = System.getenv().getOrDefault(
            "PERA_TEST_DB_URL", "jdbc:postgresql://localhost:55432/pera_sales");
    private static final String USER = System.getenv().getOrDefault("PERA_TEST_DB_USER", "pera");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "PERA_TEST_DB_PASSWORD", "pera_dev_password");

    private static final int CONCURRENT_INVOICES = 12;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final String ISSUER = "89890001K";

    /** Condición de JUnit: se evalúa antes de levantar el contexto de Spring. */
    static boolean localPostgresIsAvailable() {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return connection.isValid(2);
        } catch (Exception unavailable) {
            return false;
        }
    }

    @Autowired VerifactuChainService chain;
    @Autowired CommercialDocumentRepository documents;
    @Autowired VerifactuRecordRepository records;
    @Autowired InvoiceChainHeadRepository chainHeads;
    @Autowired TransactionTemplate transactions;

    private final UUID companyId = UUID.randomUUID();

    /**
     * La prueba comparte base con el entorno de desarrollo, así que se lleva lo suyo al terminar.
     * Sin esto, cada arranque de start-local.ps1 dejaría otra tanda de registros de prueba.
     */
    @AfterEach
    void removeTestData() {
        transactions.executeWithoutResult(status -> {
            records.deleteAll(records.findAll().stream()
                    .filter(record -> companyId.equals(record.getCompanyId())).toList());
            chainHeads.findByCompanyId(companyId).ifPresent(chainHeads::delete);
            documents.deleteAll(documents.findAll().stream()
                    .filter(document -> companyId.equals(document.getCompanyId())).toList());
        });
    }

    @Test
    void concurrentIssuancesProduceOneUnbrokenChain() throws Exception {
        LocalDate issueDate = LocalDate.now();
        List<UUID> documentIds = createDocuments(companyId, issueDate);

        // Todos los hilos esperan en la barrera y arrancan a la vez: sin eso se ejecutarían en
        // serie por casualidad y la prueba no probaría nada.
        CyclicBarrier startTogether = new CyclicBarrier(CONCURRENT_INVOICES);
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_INVOICES);
        try {
            List<Future<VerifactuRecord>> results = pool.invokeAll(documentIds.stream()
                    .map(documentId -> (Callable<VerifactuRecord>) () -> {
                        startTogether.await(30, TimeUnit.SECONDS);
                        return transactions.execute(status -> chain.append(companyId,
                                request(documentId, issueDate)));
                    })
                    .toList(), 60, TimeUnit.SECONDS);

            for (Future<VerifactuRecord> result : results) {
                assertThat(result.isCancelled()).as("ninguna emisión debe quedarse sin completar").isFalse();
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertChainIsIntact();
    }

    private ChainedRecordRequest request(UUID documentId, LocalDate issueDate) {
        return new ChainedRecordRequest(documentId, VerifactuRecordType.ALTA, ISSUER,
                "CONC-" + documentId.toString().substring(0, 8), issueDate, InvoiceKind.F1, null,
                new BigDecimal("21.00"), new BigDecimal("121.00"), MADRID, null);
    }

    private List<UUID> createDocuments(UUID companyId, LocalDate issueDate) {
        return transactions.execute(status -> java.util.stream.IntStream.range(0, CONCURRENT_INVOICES)
                .mapToObj(index -> documents.save(new CommercialDocument(companyId,
                        "CONC-" + index + "-" + UUID.randomUUID(), DocumentType.INVOICE, UUID.randomUUID(),
                        "C001", "Cliente de concurrencia", issueDate, null, "EUR", null, null, null)).getId())
                .toList());
    }

    /**
     * Recorre la cadena de la empresa y la reconstruye desde cero: cada registro debe declarar como
     * huella anterior la del registro previo, y su propia huella debe volver a salir al recalcularla.
     */
    private void assertChainIsIntact() {
        List<VerifactuRecord> chainRecords = records.findAll().stream()
                .filter(record -> companyId.equals(record.getCompanyId()))
                .sorted(java.util.Comparator.comparingLong(VerifactuRecord::getSequenceNumber))
                .toList();

        assertThat(chainRecords).hasSize(CONCURRENT_INVOICES);

        String previous = null;
        long expectedSequence = 1L;
        for (VerifactuRecord record : chainRecords) {
            assertThat(record.getSequenceNumber())
                    .as("la secuencia no puede tener huecos ni repeticiones")
                    .isEqualTo(expectedSequence);
            assertThat(record.getPreviousFingerprint())
                    .as("cada registro debe encadenar con el anterior")
                    .isEqualTo(previous);

            String recomputed = RecordFingerprint.of(FingerprintInput.forAlta(record.getIssuerTaxId(),
                    record.getInvoiceNumber(), record.getInvoiceDate(), record.getInvoiceKind(),
                    record.getTotalTaxAmount(), record.getTotalAmount(), previous,
                    record.getGeneratedAt().atZone(MADRID)));
            assertThat(recomputed)
                    .as("la huella guardada debe reproducirse al recalcularla")
                    .isEqualTo(record.getFingerprint());

            previous = record.getFingerprint();
            expectedSequence++;
        }

        assertThat(chainRecords.stream().map(VerifactuRecord::getFingerprint).distinct().count())
                .as("no puede haber dos registros con la misma huella")
                .isEqualTo(CONCURRENT_INVOICES);
    }
}
